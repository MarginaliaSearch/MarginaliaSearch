package nu.marginalia.rss.svc;

import com.google.inject.Inject;
import com.opencsv.CSVReader;
import nu.marginalia.WmsaHome;
import nu.marginalia.contenttype.ContentType;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.coordination.DomainLock;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.rss.db.FeedDbWriter;
import nu.marginalia.rss.model.FeedDefinition;
import nu.marginalia.rss.model.FeedItem;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.util.SimpleBlockingThreadPool;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

public class FeedFetcherService {
    private static final int MAX_FEED_ITEMS = 10;
    private static final Logger logger = LoggerFactory.getLogger(FeedFetcherService.class);

    private final FeedDb feedDb;
    private final FileStorageService fileStorageService;
    private final NodeConfigurationService nodeConfigurationService;
    private final ServiceHeartbeat serviceHeartbeat;
    private final ExecutorClient executorClient;

    private final DomainCoordinator domainCoordinator;

    private final HttpClient httpClient;

    private volatile boolean updating;

    @Inject
    public FeedFetcherService(FeedDb feedDb,
                              DomainCoordinator domainCoordinator,
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
        this.domainCoordinator = domainCoordinator;

        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setSocketTimeout(15, TimeUnit.SECONDS)
                .setConnectTimeout(15, TimeUnit.SECONDS)
                .setValidateAfterInactivity(TimeValue.ofSeconds(5))
                .build();


        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(2)
                .setMaxConnTotal(50)
                .setDefaultConnectionConfig(connectionConfig)
                .build();

        connectionManager.setDefaultSocketConfig(SocketConfig.custom()
                .setSoLinger(TimeValue.ofSeconds(-1))
                .setSoTimeout(Timeout.ofSeconds(10))
                .build()
        );

        Thread.ofPlatform().daemon(true).start(() -> {
            try {
                for (;;) {
                    TimeUnit.SECONDS.sleep(15);
                    logger.info("Connection pool stats: {}", connectionManager.getTotalStats());
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        final RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.IGNORE)
                .setResponseTimeout(10, TimeUnit.SECONDS)
                .setConnectionRequestTimeout(5, TimeUnit.MINUTES)
                .build();

        httpClient = HttpClients.custom()
                .setDefaultRequestConfig(defaultRequestConfig)
                .setConnectionManager(connectionManager)
                .setUserAgent(WmsaHome.getUserAgent().uaIdentifier())
                .setConnectionManager(connectionManager)
                .setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
                    // Default keep-alive duration is 3 minutes, but this is too long for us,
                    // as we are either going to re-use it fairly quickly or close it for a long time.
                    //
                    // So we set it to 30 seconds or clamp the server-provided value to a minimum of 10 seconds.
                    private static final TimeValue defaultValue = TimeValue.ofSeconds(30);

                    @Override
                    public TimeValue getKeepAliveDuration(HttpResponse response, HttpContext context) {
                        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderElements.KEEP_ALIVE);

                        while (it.hasNext()) {
                            final HeaderElement he = it.next();
                            final String param = he.getName();
                            final String value = he.getValue();

                            if (value == null)
                                continue;
                            if (!"timeout".equalsIgnoreCase(param))
                                continue;

                            try {
                                long timeout = Long.parseLong(value);
                                timeout = Math.clamp(timeout, 30, defaultValue.toSeconds());
                                return TimeValue.ofSeconds(timeout);
                            } catch (final NumberFormatException ignore) {
                                break;
                            }
                        }
                        return defaultValue;
                    }
                })
                .build();

    }

    public enum UpdateMode {
        CLEAN,
        REFRESH
    }

    public void updateFeeds(UpdateMode updateMode) throws IOException {
        if (updating) // Prevent concurrent updates
        {
            throw new IllegalStateException("Already updating feeds, refusing to start another update");
        }


        try (FeedDbWriter writer = feedDb.createWriter();
             ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();
             FeedJournal feedJournal = FeedJournal.create();
             var heartbeat = serviceHeartbeat.createServiceAdHocTaskHeartbeat("Update Rss Feeds")
        ) {
            updating = true;

            // Read the feed definitions from the database, if they are not available, read them from the system's
            // RSS exports instead

            Collection<FeedDefinition> definitions = feedDb.getAllFeeds();
            Map<String, Integer> errorCounts = feedDb.getAllErrorCounts();

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
                    try {
                        EdgeDomain domain = new EdgeDomain(feed.domain());
                        var oldData = feedDb.getFeed(domain);

                        @Nullable
                        String ifModifiedSinceDate = switch(updateMode) {
                            case REFRESH -> getIfModifiedSinceDate(feedDb);
                            case CLEAN -> null;
                        };

                        @Nullable
                        String ifNoneMatchTag = switch (updateMode) {
                            case REFRESH -> feedDb.getEtag(domain);
                            case CLEAN -> null;
                        };

                        FetchResult feedData;
                        try (DomainLock domainLock = domainCoordinator.lockDomain(new EdgeDomain(feed.domain()))) {
                            feedData = fetchFeedData(feed, fetchExecutor, ifModifiedSinceDate, ifNoneMatchTag);
                            TimeUnit.SECONDS.sleep(1); // Sleep before we yield the lock to avoid hammering the server from multiple processes
                        } catch (Exception ex) {
                            feedData = new FetchResult.TransientError();
                        }

                        switch (feedData) {
                            case FetchResult.Success(String value, String etag) -> {
                                writer.saveEtag(feed.domain(), etag);
                                writer.saveFeed(parseFeed(value, feed));

                                feedJournal.record(feed.feedUrl(), value);
                            }
                            case FetchResult.NotModified() -> {
                                writer.saveEtag(feed.domain(), ifNoneMatchTag);
                                writer.saveFeed(oldData);
                            }
                            case FetchResult.TransientError() -> {
                                int errorCount = errorCounts.getOrDefault(feed.domain().toLowerCase(), 0);
                                writer.setErrorCount(feed.domain().toLowerCase(), ++errorCount);

                                if (errorCount < 5) {
                                    // Permit the server a few days worth of retries before we drop the feed entirely
                                    writer.saveFeed(oldData);
                                }
                            }
                            case FetchResult.PermanentError() -> {
                            } // let the definition be forgotten about
                        }

                    }
                    finally {
                        if ((definitionsUpdated.incrementAndGet() % 1_000) == 0) {
                            // Update the progress every 1k feeds, to avoid hammering the database and flooding the logs
                            heartbeat.progress("Updated " + definitionsUpdated + "/" + totalDefinitions + " feeds", definitionsUpdated.get(), totalDefinitions);
                        }
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

    @Nullable
    static String getIfModifiedSinceDate(FeedDb feedDb) {

        // If the db is fresh, we don't send If-Modified-Since
        if (!feedDb.hasData())
            return null;

        Instant cutoffInstant = feedDb.getFetchTime();

        // If we're unable to establish fetch time, we don't send If-Modified-Since
        if (cutoffInstant == Instant.EPOCH)
            return null;

        return cutoffInstant.atZone(ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    private FetchResult fetchFeedData(FeedDefinition feed,
                                      ExecutorService executorService,
                                      @Nullable String ifModifiedSinceDate,
                                      @Nullable String ifNoneMatchTag)
    {
        try {
            URI uri = new URI(feed.feedUrl());

            var requestBuilder = ClassicRequestBuilder.get(uri)
                    .setHeader("User-Agent", WmsaHome.getUserAgent().uaIdentifier())
                    .setHeader("Accept-Encoding", "gzip")
                    .setHeader("Accept", "text/*, */*;q=0.9");

            // Set the If-Modified-Since or If-None-Match headers if we have them
            // though since there are certain idiosyncrasies in server implementations,
            // we avoid setting both at the same time as that may turn a 304 into a 200.
            if (ifNoneMatchTag != null) {
                requestBuilder.addHeader("If-None-Match", ifNoneMatchTag);
            } else if (ifModifiedSinceDate != null) {
                requestBuilder.addHeader("If-Modified-Since", ifModifiedSinceDate);
            }

            return httpClient.execute(requestBuilder.build(), rsp -> {
                try {
                    logger.info("Code: {}, URL: {}", rsp.getCode(), uri);

                    switch (rsp.getCode()) {
                        case 200 -> {
                            if (rsp.getEntity() == null) {
                                return new FetchResult.TransientError(); // No content to read, treat as transient error
                            }
                            byte[] responseData = EntityUtils.toByteArray(rsp.getEntity());

                            // Decode the response body based on the Content-Type header
                            Header contentTypeHeader = rsp.getFirstHeader("Content-Type");
                            if (contentTypeHeader == null) {
                                return new FetchResult.TransientError();
                            }
                            String contentType = contentTypeHeader.getValue();
                            String bodyText = DocumentBodyToString.getStringData(ContentType.parse(contentType), responseData);

                            // Grab the ETag header if it exists
                            Header etagHeader = rsp.getFirstHeader("ETag");
                            String newEtagValue = etagHeader == null ? null : etagHeader.getValue();

                            return new FetchResult.Success(bodyText, newEtagValue);
                        }
                        case 304 -> {
                            return new FetchResult.NotModified(); // via If-Modified-Since semantics
                        }
                        case 404 -> {
                            return new FetchResult.PermanentError(); // never try again
                        }
                        default -> {
                            return new FetchResult.TransientError(); // we try again later
                        }
                    }
                }
                catch (Exception ex) {
                    return new FetchResult.PermanentError(); // treat as permanent error
                }
                finally {
                    EntityUtils.consumeQuietly(rsp.getEntity());
                }
            });
        }
        catch (Exception ex) {
            logger.debug("Error fetching feed", ex);
        }

        return new FetchResult.TransientError();
    }

    public sealed interface FetchResult {
        record Success(String value, String etag) implements FetchResult {}
        record NotModified() implements FetchResult {}
        record TransientError() implements FetchResult {}
        record PermanentError()  implements FetchResult {}
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

    public FeedItems parseFeed(String feedData, FeedDefinition definition) {
        try {
            List<SimpleFeedParser.ItemData> rawItems = SimpleFeedParser.parse(feedData);

            boolean keepUriFragment = rawItems.size() < 2 || areFragmentsDisparate(rawItems);

            var items = rawItems.stream()
                    .map(item -> FeedItem.fromItem(item, keepUriFragment))
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

    /** Decide whether to keep URI fragments in the feed items.
     * <p></p>
     * We keep fragments if there are multiple different fragments in the items.
     *
     * @param items The items to check
     * @return True if we should keep the fragments, false otherwise
     */
    private boolean areFragmentsDisparate(List<SimpleFeedParser.ItemData> items) {
        Set<String> seenFragments = new HashSet<>();

        try {
            for (var item : items) {
                if (item.url().isBlank()) {
                    continue;
                }

                var link = item.url();
                if (!link.contains("#")) {
                    continue;
                }

                var fragment = new URI(link).getFragment();
                if (fragment != null) {
                    seenFragments.add(fragment);
                }
            }
        }
        catch (URISyntaxException e) {
            logger.debug("Exception", e);
            return true; // safe default
        }

        return seenFragments.size() > 1;
    }

    static class IsFeedItemDateValid implements Predicate<FeedItem> {
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
