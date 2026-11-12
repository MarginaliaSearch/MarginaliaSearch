package nu.marginalia.rss.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.opencsv.CSVReader;
import nu.marginalia.WmsaHome;
import nu.marginalia.contenttype.ContentType;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.coordination.DomainLock;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.rss.db.FeedDbWriter;
import nu.marginalia.rss.model.FeedDefinition;
import nu.marginalia.rss.model.FeedItem;
import nu.marginalia.rss.model.FeedItems;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.control.ServiceAdHocTaskHeartbeat;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageType;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.*;
import java.util.zip.GZIPInputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Predicate;

@Singleton
public class FeedFetcherService {
    private static final int MAX_FEED_ITEMS = 10;
    static final int MAX_FEED_BYTES = 8 * 1024 * 1024;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(30);
    private static final int FETCH_CONCURRENCY = 64;
    private static final Duration UPDATE_TIMEOUT = Duration.ofHours(1);
    private static final Duration DOMAIN_TIMEOUT = Duration.ofSeconds(5);
    private static final Logger logger = LoggerFactory.getLogger(FeedFetcherService.class);

    private final FeedDb feedDb;
    private final DomainTypes domainTypes;
    private final FileStorageService fileStorageService;
    private final NodeConfigurationService nodeConfigurationService;
    private final ServiceHeartbeat serviceHeartbeat;
    private final ExecutorClient executorClient;

    private final DomainCoordinator domainCoordinator;

    private final HttpClient httpClient;

    private volatile boolean updating = false;
    private volatile boolean requestStop = false;
    private Thread updateThread;

    private volatile String feedDataHash;

    @Inject
    public FeedFetcherService(FeedDb feedDb,
                              DomainTypes domainTypes,
                              DomainCoordinator domainCoordinator,
                              FileStorageService fileStorageService,
                              NodeConfigurationService nodeConfigurationService,
                              ServiceHeartbeat serviceHeartbeat,
                              ExecutorClient executorClient)
    {
        this.feedDb = feedDb;
        this.domainTypes = domainTypes;
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

        final RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.IGNORE)
                .setResponseTimeout(10, TimeUnit.SECONDS)
                .setConnectionRequestTimeout(5, TimeUnit.SECONDS)
                .build();

        httpClient = HttpClients.custom()
                .setDefaultRequestConfig(defaultRequestConfig)
                .setConnectionManager(connectionManager)
                .setUserAgent(WmsaHome.getUserAgent().uaIdentifier())
                .setConnectionManager(connectionManager)
                .disableContentCompression()
                // Retry on the next feed update instead of holding a worker through Retry-After.
                .disableAutomaticRetries()
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

    public String getFeedDataHash() throws Exception {
        if (!feedDb.isEnabled()) {
            throw new UnsupportedOperationException("FeedDB disabled on this node");
        }

        if (feedDataHash != null)
            return feedDataHash;

        return (feedDataHash = feedDb.getDataHash());
    }

    public void start(UpdateMode updateMode) {
        if (updating) {
            return;
        }
        updating = true;
        requestStop = false;
        updateThread = Thread.ofPlatform().name("Feed update").start(() -> updateFeedsSynchronous(updateMode));
    }

    public boolean isRunning() {
        return updating;
    }

    public void stop() throws InterruptedException {
        if (updating)
            updateThread.interrupt();
        if (updateThread != null)
            updateThread.join();
        updateThread = null;
        updating = false;
    }

    void updateFeedsSynchronous(UpdateMode updateMode) {
        updateFeedsSynchronous(updateMode, UPDATE_TIMEOUT);
    }

    void updateFeedsSynchronous(UpdateMode updateMode, Duration timeout) {
        try (FeedDbWriter writer = feedDb.createWriter();
             FeedJournal feedJournal = FeedJournal.create();
             var heartbeat = serviceHeartbeat.createServiceAdHocTaskHeartbeat("Update Rss Feeds"))
        {
            Collection<FeedDefinition> definitions = feedDb.getAllFeeds();
            Map<String, Integer> errorCounts = feedDb.getAllErrorCounts();

            if (definitions == null || definitions.isEmpty() || updateMode == UpdateMode.CLEAN) {
                definitions = readDefinitionsFromSystem();
            }
            definitions = mergeSmallWebDatasetFeeds(definitions);

            logger.info("Found {} feed definitions", definitions.size());
            fetchUpdates(definitions, updateMode, writer, feedJournal, heartbeat, errorCounts, timeout);

            feedDb.switchDb(writer);
            feedDataHash = null;
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.info("Feed update interrupted");
        }
        catch (TimeoutException ex) {
            logger.warn("Feed update exceeded {} and was discarded", timeout);
        }
        catch (SQLException | IOException | ExecutionException | RuntimeException ex) {
            logger.error("Error updating feeds", ex);
        }
        finally {
            updating = false;
        }
    }

    private void fetchUpdates(Collection<FeedDefinition> definitions,
                              UpdateMode updateMode,
                              FeedDbWriter writer,
                              FeedJournal journal,
                              ServiceAdHocTaskHeartbeat heartbeat,
                              Map<String, Integer> errorCounts,
                              Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException, IOException
    {
        long deadline = System.nanoTime() + timeout.toNanos();
        int today = (int) LocalDate.now().toEpochDay();
        int completed = 0;
        var pending = definitions.iterator();
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        ExecutorService fetchExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var results = new ExecutorCompletionService<FeedUpdate>(workers);
        Set<Future<FeedUpdate>> active = new HashSet<>();

        try {
            while (pending.hasNext() || !active.isEmpty()) {
                if (Thread.currentThread().isInterrupted() || requestStop) {
                    throw new InterruptedException();
                }
                if (System.nanoTime() >= deadline) {
                    throw new TimeoutException();
                }

                while (pending.hasNext() && active.size() < FETCH_CONCURRENCY) {
                    var feed = pending.next();
                    active.add(results.submit(() -> fetchUpdate(feed, updateMode, today, fetchExecutor)));
                }

                var future = results.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (future == null) {
                    throw new TimeoutException();
                }
                active.remove(future);
                saveUpdate(future.get(), writer, journal, errorCounts);

                if (++completed % 1_000 == 0) {
                    heartbeat.progress("Updated " + completed + "/" + definitions.size() + " feeds", completed, definitions.size());
                }
            }
        }
        finally {
            for (var future : active) {
                future.cancel(true);
            }
            workers.shutdownNow();
            fetchExecutor.shutdownNow();
            // Workers only return results; a task that is slow to cancel cannot write to the closing database.
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Feed workers did not terminate within five seconds");
            }
        }
    }

    private record FeedUpdate(FeedDefinition feed, FeedItems oldData, String oldEtag,
                              int lastFetch, int fetchDate, FetchResult result) {}

    private FeedUpdate fetchUpdate(FeedDefinition feed, UpdateMode updateMode, int today, ExecutorService fetchExecutor)
            throws InterruptedException
    {
        EdgeDomain domain = new EdgeDomain(feed.domain());
        FeedItems oldData = feedDb.getFeed(domain);
        int lastFetch = feedDb.getLastFetch(domain);
        String etag = updateMode == UpdateMode.REFRESH ? feedDb.getEtag(domain) : null;
        String modifiedSince = updateMode == UpdateMode.REFRESH ? getIfModifiedSinceDate(feedDb) : null;
        DomainSkip skip = shouldSkip(domain, lastFetch, today);
        FetchResult result;
        int fetchDate = 0;

        if (skip == DomainSkip.SKIP_TODAY) {
            result = new FetchResult.NotModified();
        }
        else {
            var lock = domainCoordinator.tryLockDomain(domain, DOMAIN_TIMEOUT);
            if (lock.isEmpty()) {
                result = new FetchResult.Deferred();
            }
            else {
                try (DomainLock domainLock = lock.get()) {
                    result = fetchFeedData(feed, fetchExecutor, modifiedSince, etag);
                    if (skip == DomainSkip.NO_SKIP_TODAY && !(result instanceof FetchResult.TransientError)) {
                        fetchDate = today;
                    }
                }
            }
        }

        return new FeedUpdate(feed, oldData, etag, lastFetch, fetchDate, result);
    }

    private void saveUpdate(FeedUpdate update, FeedDbWriter writer, FeedJournal journal, Map<String, Integer> errorCounts)
            throws IOException
    {
        FeedDefinition feed = update.feed();
        String domain = feed.domain().toLowerCase();
        if (update.fetchDate() > 0) {
            writer.saveFetchDate(domain, update.fetchDate());
        }

        switch (update.result()) {
            case FetchResult.Success(String value, String etag) -> {
                writer.saveEtag(domain, etag);
                writer.saveFeed(parseFeed(value, feed));
                journal.record(feed.feedUrl(), value);
            }
            case FetchResult.NotModified() -> {
                writer.saveEtag(domain, update.oldEtag());
                writer.saveFeed(update.oldData());
            }
            case FetchResult.Deferred() -> {
                // Local contention says nothing about the health of the remote feed.
                writer.saveFeed(update.oldData());
                writer.saveEtag(domain, update.oldEtag());
                writer.saveFetchDate(domain, update.lastFetch());
                int errorCount = errorCounts.getOrDefault(domain, 0);
                if (errorCount > 0) {
                    writer.setErrorCount(domain, errorCount);
                }
            }
            case FetchResult.TransientError() -> {
                int errorCount = errorCounts.getOrDefault(domain, 0) + 1;
                writer.setErrorCount(domain, errorCount);
                if (errorCount < 10) {
                    writer.saveFeed(update.oldData());
                }
            }
            case FetchResult.PermanentError() -> {
            }
        }
    }

    private enum DomainSkip {
        NEVER,
        NO_SKIP_TODAY,
        SKIP_TODAY,
    }

    private DomainSkip shouldSkip(EdgeDomain domain, int lastFetch, int today) {
        // We can't fetch these domains every day, since we can't hammer them with too many parallel requests,
        // they become a bottleneck
        int fetchCadence = switch (domain.topDomain) {
            case "wordpress.com" -> 4;
            case "blogspot.com" -> 4;
            case "substack.com" -> 7;
            default -> 0;
        };

        if (fetchCadence == 0) {
            return DomainSkip.NEVER;
        }

        // We lack fetch date, so let's generate one based on hash code,
        // so that we'll get a nice, reasonably uniform distribution
        if (lastFetch == 0) {
            lastFetch = today - (domain.hashCode() & 0x7FFF_FFFF) % (fetchCadence + 1);
        }

        if (today < (lastFetch + fetchCadence)) {
            return DomainSkip.SKIP_TODAY;
        }
        else {
            return DomainSkip.NO_SKIP_TODAY;
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

    FetchResult fetchFeedData(FeedDefinition feed,
                              ExecutorService executorService,
                              @Nullable String ifModifiedSinceDate,
                              @Nullable String ifNoneMatchTag) throws InterruptedException
    {
        return fetchFeedData(feed, executorService, ifModifiedSinceDate, ifNoneMatchTag, FETCH_TIMEOUT);
    }

    FetchResult fetchFeedData(FeedDefinition feed,
                              ExecutorService executorService,
                              @Nullable String ifModifiedSinceDate,
                              @Nullable String ifNoneMatchTag,
                              Duration timeout) throws InterruptedException
    {
        final HttpGet request;
        try {
            request = new HttpGet(feed.feedUrl());
        }
        catch (IllegalArgumentException ex) {
            logger.warn("Invalid feed URL: {}", feed.feedUrl(), ex);
            return new FetchResult.TransientError();
        }

        request.setHeader("User-Agent", WmsaHome.getUserAgent().uaIdentifier());
        request.setHeader("Accept-Encoding", "gzip");
        request.setHeader("Accept", "text/*, */*;q=0.9");

        // Some servers return 200 instead of 304 when both validators are present.
        if (ifNoneMatchTag != null) {
            request.setHeader("If-None-Match", ifNoneMatchTag);
        } else if (ifModifiedSinceDate != null) {
            request.setHeader("If-Modified-Since", ifModifiedSinceDate);
        }

        var future = executorService.submit(() -> fetch(request));
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
        catch (TimeoutException ex) {
            logger.warn("Timed out fetching feed after {}: {}", timeout, feed.feedUrl());
            return new FetchResult.TransientError();
        }
        catch (ExecutionException ex) {
            logger.debug("Error fetching feed {}", feed.feedUrl(), ex.getCause());
            return new FetchResult.TransientError();
        }
        finally {
            if (!future.isDone()) {
                // Interrupting a worker alone does not reliably close a blocking HTTP socket.
                request.cancel();
                future.cancel(true);
            }
        }
    }

    private FetchResult fetch(HttpGet request) throws IOException {
        var response = httpClient.executeOpen(null, request, null);
        boolean consumed = false;
        try {
            logger.info("Code: {}, URL: {}", response.getCode(), request.getRequestUri());

            return switch (response.getCode()) {
                case 200 -> {
                    if (response.getEntity() == null) {
                        yield new FetchResult.TransientError();
                    }

                    Optional<String> bodyText = decodeBodyText(request, response);
                    if (bodyText.isEmpty()) {
                        yield new FetchResult.TransientError();
                    }
                    consumed = true;

                    Header etag = response.getFirstHeader("ETag");
                    yield new FetchResult.Success(bodyText.get(), etag == null ? null : etag.getValue());
                }
                case 304 -> {
                    consumed = true;
                    yield new FetchResult.NotModified();
                }
                case 404 -> new FetchResult.PermanentError();
                default -> new FetchResult.TransientError();
            };
        }
        finally {
            // Closing an unread entity drains it for reuse, which can stall on error bodies too.
            if (consumed) {
                response.close();
            }
            else {
                request.cancel();
                Closer.closeQuietly(response);
            }
        }
    }

    public sealed interface FetchResult {
        record Success(String value, String etag) implements FetchResult {}
        record NotModified() implements FetchResult {}
        record Deferred() implements FetchResult {}
        record TransientError() implements FetchResult {}
        record PermanentError()  implements FetchResult {}
    }

    private Optional<String> decodeBodyText(HttpGet request, ClassicHttpResponse response) throws IOException {
        Header contentTypeHeader = response.getFirstHeader("Content-Type");
        if (contentTypeHeader == null) {
            return Optional.empty();
        }
        ContentType contentType = ContentType.parse(contentTypeHeader.getValue());

        InputStream content = response.getEntity().getContent();
        Header encoding = response.getFirstHeader("Content-Encoding");
        if (encoding != null && "gzip".equalsIgnoreCase(encoding.getValue())) {
            content = new GZIPInputStream(content);
        }

        try (InputStream stream = content) {
            try {
                byte[] bytes = stream.readNBytes(MAX_FEED_BYTES + 1);
                if (bytes.length > MAX_FEED_BYTES) {
                    throw new IOException("Feed exceeds " + MAX_FEED_BYTES + " decoded bytes");
                }
                return Optional.of(DocumentBodyToString.getStringData(contentType, bytes));
            }
            catch (IOException | RuntimeException ex) {
                request.cancel();
                throw ex;
            }
        }
    }

    private InputStream openStream(URL url) throws IOException {
        var connection = url.openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(10_000);
        return connection.getInputStream();
    }

    public Collection<FeedDefinition> readDefinitionsFromSystem() throws IOException {
        Collection<FileStorage> storages = getLatestFeedStorages();
        List<FeedDefinition> feedDefinitionList = new ArrayList<>();

        for (var storage : storages) {
            var url = executorClient.remoteFileURL(storage, "feeds.csv.gz");
            if (null == url) {
                continue;
            }
            logger.info("Fetching from {}", url);

            try (var feedStream = new GZIPInputStream(openStream(url))) {
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
            catch (FileNotFoundException ex) {
                logger.error("No feeds.csv.gz found at {} (404), aborting feed read", url);
                throw ex;
            }
        }

        return feedDefinitionList;
    }


    private Collection<FeedDefinition> mergeSmallWebDatasetFeeds(Collection<FeedDefinition> existing) {
        String sourceUrl = domainTypes.getUrlForSelection(DomainTypes.Type.SMALL);
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return existing;
        }

        Collection<FeedDefinition> datasetFeeds = downloadFeedDefinitions(sourceUrl);
        if (datasetFeeds.isEmpty()) {
            return existing;
        }

        Set<String> existingDomains = new HashSet<>();
        for (var def : existing) {
            existingDomains.add(def.domain().toLowerCase());
        }

        List<FeedDefinition> merged = new ArrayList<>(existing);
        int added = 0;
        for (var def : datasetFeeds) {
            if (existingDomains.add(def.domain().toLowerCase())) {
                merged.add(def);
                added++;
            }
        }

        if (added > 0) {
            logger.info("Added {} feed definitions from the small web dataset", added);
        }

        return merged;
    }


    /** Download a list of feed URLs from the given source URL and convert them to FeedDefinitions. */
    private Collection<FeedDefinition> downloadFeedDefinitions(String sourceUrl) {
        List<FeedDefinition> definitions = new ArrayList<>();

        byte[] streamBytes;
        try (var urlStream = openStream(URI.create(sourceUrl).toURL())) {
            streamBytes = urlStream.readNBytes(32*1024*1024);  // zip bomb safety
        }
        catch (IOException ex) {
            logger.error("Error downloading feed definitions from small web dataset: {}", sourceUrl, ex);
            return List.of();
        }

        var scanner = new Scanner(new ByteArrayInputStream(streamBytes));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            int hashIdx = line.indexOf('#');
            if (hashIdx >= 0) {
                line = line.substring(0, hashIdx);
            }
            line = line.trim();

            if (line.isBlank()) continue;

            String feedUrl = line;
            EdgeUrl.parse(feedUrl).ifPresent(url ->
                definitions.add(
                        new FeedDefinition(url.getDomain().toString(), feedUrl, null)
                )
            );
        }

        return definitions;
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
