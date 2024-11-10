package nu.marginalia.rss.svc;

import com.apptasticsoftware.rssreader.RssReader;
import com.google.inject.Inject;
import com.opencsv.CSVReader;
import nu.marginalia.WmsaHome;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.rss.model.FeedDefinition;
import nu.marginalia.rss.model.FeedItem;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.util.SimpleBlockingThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.http.HttpClient;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

public class FeedFetcherService {
    private static final int MAX_FEED_ITEMS = 10;
    private static final Logger logger = LoggerFactory.getLogger(FeedFetcherService.class);

    private final RssReader rssReader = new RssReader(
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .executor(Executors.newCachedThreadPool())
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .version(HttpClient.Version.HTTP_2)
                    .build()
    );

    private final FeedDb feedDb;
    private final FileStorageService fileStorageService;
    private final NodeConfigurationService nodeConfigurationService;
    private final ServiceHeartbeat serviceHeartbeat;
    private final ExecutorClient executorClient;

    private volatile boolean updating;

    @Inject
    public FeedFetcherService(FeedDb feedDb,
                              FileStorageService fileStorageService,
                              NodeConfigurationService nodeConfigurationService,
                              ServiceHeartbeat serviceHeartbeat,
                              ExecutorClient executorClient)
    {
        this.feedDb = feedDb;
        this.fileStorageService = fileStorageService;
        this.nodeConfigurationService = nodeConfigurationService;
        this.serviceHeartbeat = serviceHeartbeat;
        this.executorClient = executorClient;

        rssReader.addHeader("User-Agent", WmsaHome.getUserAgent().uaIdentifier() + "  RSS Feed Fetcher");
    }

    public enum UpdateMode {
        CLEAN,
        REFRESH
    };

    public void updateFeeds(UpdateMode updateMode) throws IOException {
        if (updating) // Prevent concurrent updates
        {
            throw new IllegalStateException("Already updating feeds, refusing to start another update");
        }

        try (var writer = feedDb.createWriter();
            var heartbeat = serviceHeartbeat.createServiceAdHocTaskHeartbeat("Update Rss Feeds")
        ) {
            updating = true;

            // Read the feed definitions from the database, if they are not available, read them from the system's
            // RSS exports instead

            Collection<FeedDefinition> definitions = feedDb.getAllFeeds();

            // If we didn't get any definitions, or a clean update is requested, read the definitions from the system
            // instead
            if (definitions == null || updateMode == UpdateMode.CLEAN) {
                definitions = readDefinitionsFromSystem();
            }

            logger.info("Found {} feed definitions", definitions.size());

            final AtomicInteger definitionsUpdated = new AtomicInteger(0);
            final int totalDefinitions = definitions.size();

            SimpleBlockingThreadPool executor = new SimpleBlockingThreadPool("FeedFetcher", 64, 4);

            for (var feed : definitions) {
                executor.submitQuietly(() -> {
                    var oldData = feedDb.getFeed(new EdgeDomain(feed.domain()));

                    // If we have existing data, we might skip updating it with a probability that increases with time,
                    // this is to avoid hammering the feeds that are updated very rarely and save some time and resources
                    // on our end

                    if (!oldData.isEmpty()) {
                        Duration duration = feed.durationSinceUpdated();
                        long daysSinceUpdate = duration.toDays();


                        if (daysSinceUpdate > 2 && ThreadLocalRandom.current()
                                .nextInt(1, 1 + (int) Math.min(10, daysSinceUpdate) / 2) > 1)
                        {
                            // Skip updating this feed, just write the old data back instead
                            writer.saveFeed(oldData);
                            return;
                        }
                    }


                    var items = fetchFeed(feed);
                    if (!items.isEmpty()) {
                        writer.saveFeed(items);
                    }

                    if ((definitionsUpdated.incrementAndGet() % 1_000) == 0) {
                        // Update the progress every 1k feeds, to avoid hammering the database and flooding the logs
                        heartbeat.progress("Updated " + definitionsUpdated + "/" + totalDefinitions + " feeds", definitionsUpdated.get(), totalDefinitions);
                    }
                });
            }

            executor.shutDown();
            // Wait for the executor to finish, but give up after 60 minutes to avoid hanging indefinitely
            for (int waitedMinutes = 0; waitedMinutes < 60; waitedMinutes++) {
                if (executor.awaitTermination(1, TimeUnit.MINUTES)) break;
            }
            executor.shutDownNow();

            // Wait for any in-progress writes to finish before switching the database
            // in case we ended up murdering the writer with shutDownNow.  It's a very
            // slim chance but this increases the odds of a clean switch over.

            TimeUnit.SECONDS.sleep(5);

            feedDb.switchDb(writer);

        } catch (SQLException|InterruptedException e) {
            logger.error("Error updating feeds", e);
        }
        finally {
            updating = false;
        }
    }

    public Collection<FeedDefinition> readDefinitionsFromSystem() throws IOException {
        Collection<FileStorage> storages = getLatestFeedStorages();
        List<FeedDefinition> feedDefinitionList = new ArrayList<>();

        for (var storage : storages) {
            var url = executorClient.remoteFileURL(storage, "feeds.csv.gz");

            try (var feedStream = new GZIPInputStream(url.openStream())) {
                CSVReader reader = new CSVReader(new java.io.InputStreamReader(feedStream));

                for (String[] row : reader) {
                    if (row.length < 3) {
                        continue;
                    }
                    var domain = row[0].trim();
                    var feedUrl = row[2].trim();

                    feedDefinitionList.add(new FeedDefinition(domain, feedUrl, null));
                }

            }
        }

        return feedDefinitionList;
    }

    private Collection<FileStorage> getLatestFeedStorages() {
        // Find the newest feed storage for each node

        Map<Integer, FileStorage> newestStorageByNode = new HashMap<>();

        for (var node : nodeConfigurationService.getAll()) {
            int nodeId = node.node();

            for (var storage: fileStorageService.getEachFileStorage(nodeId, FileStorageType.EXPORT)) {
                if (!storage.description().startsWith("Feeds "))
                    continue;

                newestStorageByNode.compute(storage.node(), new KeepNewerFeedStorage(storage));
            }

        }

        return newestStorageByNode.values();
    }


    private static class KeepNewerFeedStorage implements BiFunction<Integer, FileStorage, FileStorage> {
        private final FileStorage newValue;

        private KeepNewerFeedStorage(FileStorage newValue) {
            this.newValue = newValue;
        }

        public FileStorage apply(Integer node, @Nullable FileStorage oldValue) {
            if (oldValue == null) return newValue;

            return newValue.createDateTime().isAfter(oldValue.createDateTime())
                    ? newValue
                    : oldValue;
        }
    }

    public FeedItems fetchFeed(FeedDefinition definition) {
        try {
            var items = rssReader.read(definition.feedUrl())
                    .map(FeedItem::fromItem)
                    .filter(new IsFeedItemDateValid())
                    .sorted()
                    .limit(MAX_FEED_ITEMS)
                    .toList();

            return new FeedItems(
                    definition.domain(),
                    definition.feedUrl(),
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    items);

        } catch (Exception e) {
            logger.debug("Exception", e);
            return FeedItems.none();
        }
    }

    private static class IsFeedItemDateValid implements Predicate<FeedItem> {
        private final String today = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        public boolean test(FeedItem item) {
            var date = item.date();

            if (date.isBlank()) {
                return false;
            }

            if (date.compareTo(today) > 0) {
                return false;
            }

            return true;
        }
    }
}
