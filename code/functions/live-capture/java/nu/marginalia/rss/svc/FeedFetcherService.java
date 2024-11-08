package nu.marginalia.rss.svc;

import com.apptasticsoftware.rssreader.RssReader;
import com.google.inject.Inject;
import com.opencsv.CSVReader;
import nu.marginalia.WmsaHome;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.rss.model.FeedDefinition;
import nu.marginalia.rss.model.FeedItem;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.http.HttpClient;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

public class FeedFetcherService {
    private static final int MAX_FEED_ITEMS = 10;
    private static final Logger logger = LoggerFactory.getLogger(FeedFetcherService.class);

    private final RssReader rssReader = new RssReader(
            HttpClient.newBuilder().executor(Executors.newWorkStealingPool(16)).build()
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
    }

    public void updateFeeds() throws IOException {
        rssReader.addHeader("User-Agent", WmsaHome.getUserAgent().uaIdentifier() + "  RSS Feed Fetcher");

        if (updating) // Prevent concurrent updates
        {
            logger.error("Already updating feeds, refusing to start another update");
            return;
        }

        try (var writer = feedDb.createWriter();
            var heartbeat = serviceHeartbeat.createServiceAdHocTaskHeartbeat("Update Rss Feeds")
        ) {
            updating = true;
            var definitions = readDefinitions();

            logger.info("Found {} feed definitions", definitions.size());

            int updated = 0;
            for (var feed: definitions) {

                var items = fetchFeed(feed);
                if (!items.isEmpty()) {
                    writer.saveFeed(items);
                }

                heartbeat.progress("Updated " + updated + " feeds", ++updated, definitions.size());
            }

            feedDb.switchDb(writer);

        } catch (SQLException e) {
            logger.error("Error updating feeds", e);
        }
        finally {
            updating = false;
        }
    }

    public Collection<FeedDefinition> readDefinitions() throws IOException {
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

                    feedDefinitionList.add(new FeedDefinition(domain, feedUrl));
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
            logger.warn("Failed to read feed {}: {}", definition.feedUrl(), e.getMessage());

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
