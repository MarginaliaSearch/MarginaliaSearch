package nu.marginalia.livecrawler;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.WmsaHome;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.converting.ConverterModule;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.converting.writer.ConverterBatchWriter;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.loading.documents.DocumentLoaderService;
import nu.marginalia.loading.documents.KeywordLoaderService;
import nu.marginalia.loading.domains.DbDomainIdRegistry;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.crawling.LiveCrawlRequest;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static nu.marginalia.mqapi.ProcessInboxNames.LIVE_CRAWLER_INBOX;

public class LiveCrawlerMain extends ProcessMainClass {

    private static final Logger logger =
            LoggerFactory.getLogger(LiveCrawlerMain.class);

    private final FeedsClient feedsClient;
    private final ProcessHeartbeat heartbeat;
    private final DbDomainQueries domainQueries;
    private final DomainBlacklist domainBlacklist;
    private final DomainProcessor domainProcessor;
    private final FileStorageService fileStorageService;
    private final KeywordLoaderService keywordLoaderService;
    private final DocumentLoaderService documentLoaderService;
    private final HikariDataSource dataSource;

    @Inject
    public LiveCrawlerMain(FeedsClient feedsClient,
                           Gson gson,
                           ProcessConfiguration config,
                           ProcessHeartbeat heartbeat,
                           DbDomainQueries domainQueries,
                           DomainBlacklist domainBlacklist,
                           MessageQueueFactory messageQueueFactory,
                           DomainProcessor domainProcessor,
                           FileStorageService fileStorageService,
                           KeywordLoaderService keywordLoaderService,
                           DocumentLoaderService documentLoaderService, HikariDataSource dataSource)
            throws Exception
    {
        super(messageQueueFactory, config, gson, LIVE_CRAWLER_INBOX);

        this.feedsClient = feedsClient;
        this.heartbeat = heartbeat;
        this.domainQueries = domainQueries;
        this.domainBlacklist = domainBlacklist;
        this.domainProcessor = domainProcessor;
        this.fileStorageService = fileStorageService;
        this.keywordLoaderService = keywordLoaderService;
        this.documentLoaderService = documentLoaderService;
        this.dataSource = dataSource;

        domainBlacklist.waitUntilLoaded();
    }

    public static void main(String... args) throws Exception {

        // Prevent Java from caching DNS lookups forever (filling up the system RAM as a result)
        Security.setProperty("networkaddress.cache.ttl", "3600");

        // This must run *early*
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());

        // If these aren't set properly, the JVM will hang forever on some requests
        System.setProperty("sun.net.client.defaultConnectTimeout", "30000");
        System.setProperty("sun.net.client.defaultReadTimeout", "30000");

        // We don't want to use too much memory caching sessions for https
        System.setProperty("javax.net.ssl.sessionCacheSize", "2048");

        try {
            Injector injector = Guice.createInjector(
                    new LiveCrawlerModule(),
                    new ProcessConfigurationModule("crawler"),
                    new ConverterModule(),
                    new ServiceDiscoveryModule(),
                    new DatabaseModule(false)
            );

            var crawler = injector.getInstance(LiveCrawlerMain.class);
            Instructions<LiveCrawlRequest> instructions = crawler.fetchInstructions(LiveCrawlRequest.class);

            try{
                crawler.run();
                instructions.ok();
            } catch (Exception e) {
                instructions.err();
                throw e;
            }

        } catch (Exception e) {
            logger.error("LiveCrawler failed", e);

            System.exit(1);
        }
        System.exit(0);
    }

    enum LiveCrawlState {
        PRUNE_DB,
        FETCH_LINKS,
        CRAWLING,
        PROCESSING,
        LOADING,
        CONSTRUCTING,
        DONE
    }

    private void run() throws Exception {
        Path basePath = fileStorageService.getStorageBase(FileStorageBaseType.STORAGE).asPath().resolve("live-crawl-data");

        if (!Files.isDirectory(basePath)) {
            Files.createDirectories(basePath);
        }

        run(basePath);
    }

    private void run(Path basePath) throws Exception {
        try (var processHeartbeat = heartbeat.createProcessTaskHeartbeat(LiveCrawlState.class, "LiveCrawler");
             LiveCrawlDataSet dataSet = new LiveCrawlDataSet(basePath))
        {
            final Instant cutoff = Instant.now().minus(60, ChronoUnit.DAYS);

            processHeartbeat.progress(LiveCrawlState.FETCH_LINKS);

            Map<String, List<String>> urlsPerDomain = new HashMap<>(10_000);
            feedsClient.getUpdatedDomains(cutoff, urlsPerDomain::put);

            logger.info("Fetched data for {} domains", urlsPerDomain.size());

            processHeartbeat.progress(LiveCrawlState.PRUNE_DB);

            // Remove data that is too old
            dataSet.prune(cutoff);

            processHeartbeat.progress(LiveCrawlState.CRAWLING);

            try (SimpleLinkScraper fetcher = new SimpleLinkScraper(dataSet, domainQueries, domainBlacklist);
                 var hb = heartbeat.createAdHocTaskHeartbeat("Live Crawling"))
            {
                for (Map.Entry<String, List<String>> entry : hb.wrap("Fetching", urlsPerDomain.entrySet())) {
                    EdgeDomain domain = new EdgeDomain(entry.getKey());
                    List<String> urls = entry.getValue();

                    if (urls.isEmpty())
                        continue;

                    fetcher.scheduleRetrieval(domain, urls);
                }
            }

            Path tempPath = dataSet.createWorkDir();

            try {
                processHeartbeat.progress(LiveCrawlState.PROCESSING);

                try (var hb = heartbeat.createAdHocTaskHeartbeat("Processing");
                     var writer = new ConverterBatchWriter(tempPath, 0)
                ) {
                    // Offset the documents' ordinals toward the upper range, to avoid an ID collisions with the
                    // main indexes (the maximum permissible for doc ordinal is  value is 67_108_863, so this
                    // leaves us with a lot of headroom still)
                    writer.setOrdinalOffset(67_000_000);

                    for (SerializableCrawlDataStream stream : hb.wrap("Processing", dataSet.getDataStreams())) {
                        writer.write(domainProcessor.simpleProcessing(stream, 0, Set.of("special:live")));
                    }
                }

                processHeartbeat.progress(LiveCrawlState.LOADING);

                LoaderInputData lid = new LoaderInputData(tempPath, 1);

                DomainIdRegistry domainIdRegistry = new DbDomainIdRegistry(dataSource);

                keywordLoaderService.loadKeywords(domainIdRegistry, heartbeat, lid);
                documentLoaderService.loadDocuments(domainIdRegistry, heartbeat, lid);

                keywordLoaderService.close();

            } finally {
                FileUtils.deleteDirectory(tempPath.toFile());
            }

            // Construct the index

            processHeartbeat.progress(LiveCrawlState.DONE);
        }
    }

}
