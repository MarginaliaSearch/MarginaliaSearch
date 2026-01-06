package nu.marginalia.livecrawler;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.IndexLocations;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.ConverterModule;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.converting.writer.ConverterBatchWriter;
import nu.marginalia.coordination.DomainCoordinationModule;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.livecrawler.io.HttpClientProvider;
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
import nu.marginalia.rss.db.FeedDb;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.io.CloseMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static nu.marginalia.mqapi.ProcessInboxNames.LIVE_CRAWLER_INBOX;

public class LiveCrawlerMain extends ProcessMainClass {

    private static final Logger logger =
            LoggerFactory.getLogger(LiveCrawlerMain.class);

    private final ProcessHeartbeat heartbeat;
    private final DbDomainQueries domainQueries;
    private final DomainBlacklist domainBlacklist;
    private final DomainProcessor domainProcessor;
    private final FileStorageService fileStorageService;
    private final KeywordLoaderService keywordLoaderService;
    private final DocumentLoaderService documentLoaderService;
    private final LanguageConfiguration languageConfiguration;
    private final DomainCoordinator domainCoordinator;
    private final HikariDataSource dataSource;

    @Inject
    public LiveCrawlerMain(Gson gson,
                           ProcessConfiguration config,
                           ProcessHeartbeat heartbeat,
                           DbDomainQueries domainQueries,
                           DomainBlacklist domainBlacklist,
                           MessageQueueFactory messageQueueFactory,
                           DomainProcessor domainProcessor,
                           FileStorageService fileStorageService,
                           KeywordLoaderService keywordLoaderService,
                           DocumentLoaderService documentLoaderService,
                           LanguageConfiguration languageConfiguration,
                           DomainCoordinator domainCoordinator,
                           HikariDataSource dataSource)
            throws Exception
    {
        super(messageQueueFactory, config, gson, LIVE_CRAWLER_INBOX);

        this.heartbeat = heartbeat;
        this.domainQueries = domainQueries;
        this.domainBlacklist = domainBlacklist;
        this.domainProcessor = domainProcessor;
        this.fileStorageService = fileStorageService;
        this.keywordLoaderService = keywordLoaderService;
        this.documentLoaderService = documentLoaderService;
        this.languageConfiguration = languageConfiguration;
        this.domainCoordinator = domainCoordinator;
        this.dataSource = dataSource;

        domainBlacklist.waitUntilLoaded();
    }

    public static void main(String... args) throws Exception {

        // Prevent Java from caching DNS lookups forever (filling up the system RAM as a result)
        Security.setProperty("networkaddress.cache.ttl", "3600");

        // This must run *early*
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());

        // If these aren't set properly, the JVM will hang forever on some requests
        System.setProperty("sun.net.client.defaultConnectTimeout", 
                System.getProperty("crawler.jvmConnectTimeout", "30000"));
        System.setProperty("sun.net.client.defaultReadTimeout", 
                System.getProperty("crawler.jvmReadTimeout", "30000"));

        // We don't want to use too much memory caching sessions for https
        System.setProperty("javax.net.ssl.sessionCacheSize", "2048");

        try {
            Injector injector = Guice.createInjector(
                    new LiveCrawlerModule(),
                    new DomainCoordinationModule(),
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
        Path basePath = fileStorageService
                .getStorageBase(FileStorageBaseType.STORAGE)
                .asPath()
                .resolve("live-crawl-data");

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

            /* ------------------------------------------------ */
            /* Fetch the latest domains from the feeds database */
            /* ------------------------------------------------ */

            processHeartbeat.progress(LiveCrawlState.FETCH_LINKS);
            Map<String, List<String>> urlsPerDomain;

            try (var reader = FeedDb.createReader()) {
                urlsPerDomain = reader.getLinksUpdatedSince(cutoff);
            }

            logger.info("Fetched data for {} domains", urlsPerDomain.size());


            /* ------------------------------------- */
            /* Prune the database from old entries   */
            /* ------------------------------------- */

            processHeartbeat.progress(LiveCrawlState.PRUNE_DB);

            dataSet.prune(cutoff);


            /* ------------------------------------- */
            /* Fetch the links for each domain       */
            /* ------------------------------------- */

            processHeartbeat.progress(LiveCrawlState.CRAWLING);

            CloseableHttpClient client = HttpClientProvider.createClient();
            try (SimpleLinkScraper fetcher = new SimpleLinkScraper(dataSet, domainCoordinator, domainQueries, client, domainBlacklist);
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
            finally {
                client.close(CloseMode.GRACEFUL);
            }

            Path tempPath = dataSet.createWorkDir();


            try {
                /* ------------------------------------- */
                /* Process the fetched links             */
                /* ------------------------------------- */

                processHeartbeat.progress(LiveCrawlState.PROCESSING);

                try (var hb = heartbeat.createAdHocTaskHeartbeat("Processing");
                     var writer = new ConverterBatchWriter(tempPath, 0)
                ) {
                    // We need unique document ids that do not collide with the document id from the main index,
                    // so we offset the documents' ordinals toward the upper range.
                    //
                    // The maximum permissible for doc ordinal is value is 67_108_863,
                    // so this leaves us with a lot of headroom still!
                    // Expected document count here is order of 10 :^)
                    writer.setOrdinalOffset(67_000_000);

                    for (SerializableCrawlDataStream stream : hb.wrap("Processing", dataSet.getDataStreams())) {
                        writer.write(domainProcessor.simpleProcessing(stream, 0, Set.of("special:live")));
                    }
                }


                /* ---------------------------------------------- */
                /* Load the processed data into the link database */
                /* and construct an index journal for the docs    */
                /* ---------------------------------------------- */

                processHeartbeat.progress(LiveCrawlState.LOADING);

                // Normally this is done automatically, but we need to trigger it manually to avoid
                // creating weird amalgams of mixed historical index data
                cleanIndexDir();

                LoaderInputData lid = new LoaderInputData(tempPath, 1);
                DomainIdRegistry domainIdRegistry = new DbDomainIdRegistry(dataSource);

                keywordLoaderService.loadKeywords(domainIdRegistry, heartbeat, lid);
                documentLoaderService.loadDocuments(domainIdRegistry, heartbeat, lid);

                keywordLoaderService.close();

            } finally {
                FileUtils.deleteDirectory(tempPath.toFile());
            }


            /* ------------------------------------- */
            /*  Finish up                            */
            /* ------------------------------------- */

            processHeartbeat.progress(LiveCrawlState.DONE);

            // After we return from here, the LiveCrawlActor will trigger an index construction
            // job.  Unlike all the stuff we did in this process, it's identical to the real job
            // so we don't need to do anything special from this process
        }
    }

    /** Remove the contents of the temporary index construction area
     */
    private void cleanIndexDir() throws IOException {
        Path indexConstructionArea = IndexLocations.getIndexConstructionArea(fileStorageService);

        for (var languageDefinition : languageConfiguration.languages()) {
            Path dir = IndexJournal.allocateName(indexConstructionArea, languageDefinition.isoCode());

            List<Path> indexDirContents = new ArrayList<>();
            try (var contentsStream = Files.list(dir)) {
                contentsStream.filter(Files::isRegularFile).forEach(indexDirContents::add);
            }

            for (var junkFile: indexDirContents) {
                Files.deleteIfExists(junkFile);
            }
        }
    }

}
