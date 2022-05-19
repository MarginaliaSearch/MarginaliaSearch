package nu.marginalia.wmsa.edge.crawler.domain;

import com.zaxxer.hikari.HikariDataSource;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import lombok.SneakyThrows;
import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.data_store.DataStoreService;
import nu.marginalia.wmsa.data_store.EdgeDataStoreService;
import nu.marginalia.wmsa.data_store.FileRepository;
import nu.marginalia.wmsa.data_store.client.DataStoreClient;
import nu.marginalia.wmsa.edge.archive.EdgeArchiveService;
import nu.marginalia.wmsa.edge.archive.client.ArchiveClient;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.LanguageFilter;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlProcessor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.PlainTextProcessor;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpFetcher;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpRedirectResolver;
import nu.marginalia.wmsa.edge.crawler.worker.GeoIpBlocklist;
import nu.marginalia.wmsa.edge.crawler.worker.IpBlockList;
import nu.marginalia.wmsa.edge.crawler.worker.Worker;
import nu.marginalia.wmsa.edge.crawler.worker.WorkerFactory;
import nu.marginalia.wmsa.edge.crawler.worker.data.CrawlJobsSpecification;
import nu.marginalia.wmsa.edge.crawler.worker.facade.TaskProvider;
import nu.marginalia.wmsa.edge.crawler.worker.facade.UploadFacadeDirectImpl;
import nu.marginalia.wmsa.edge.crawler.worker.results.WorkerResults;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.director.client.EdgeDirectorClient;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingQueue;

import static nu.marginalia.util.TestUtil.evalScript;
import static nu.marginalia.util.TestUtil.getConnection;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("nobuild")
@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
class DomainCrawlerTest {
    private static final Logger logger = LoggerFactory.getLogger(DomainCrawlerTest.class);
    private static HttpFetcher fetcher;
    private static LanguageFilter languageFilter;

    static DataStoreService service;
    static DataStoreClient dataStoreClient;
    static EdgeDirectorClient edgeDirectorClient;

    private static HikariDataSource dataSource;
    private static EdgeDataStoreService edgeService;

    static int testPort = TestUtil.getPort();
    private static EdgeDataStoreDaoImpl edgeDataStore;
    private static WorkerFactory workerFactory;
    private static ArchiveClient archiveClient;

    private List<CrawlJobsSpecification> crawlJobsSpecifications
            = List.of(
            new CrawlJobsSpecification(0),
            new CrawlJobsSpecification(0),
            new CrawlJobsSpecification(0),
            new CrawlJobsSpecification(0),
            new CrawlJobsSpecification(0),
            new CrawlJobsSpecification(0),
            new CrawlJobsSpecification(0),
            new CrawlJobsSpecification(0),
            new CrawlJobsSpecification(1),
            new CrawlJobsSpecification(1),
            new CrawlJobsSpecification(10),
            new CrawlJobsSpecification(10),
            new CrawlJobsSpecification(10),
            new CrawlJobsSpecification(10),
            new CrawlJobsSpecification(50),
            new CrawlJobsSpecification(50)
    );

    static LinkedList<EdgeIndexTask> indexTasks = new LinkedList<>();
    static LinkedList<EdgeIndexTask> discoverTasks = new LinkedList<>();

    @SneakyThrows
    public static HikariDataSource provideConnection() {
        var conn = getConnection();

        evalScript(conn, "sql/data-store-init.sql");
        evalScript(conn, "sql/edge-crawler-cache.sql");

        return conn;
    }


    @SneakyThrows
    @BeforeAll
    public static void setUpClass() {
        Spark.port(testPort);
        System.setProperty("service-name", "test");

        dataSource = provideConnection();
        dataSource.setKeepaliveTime(100);
        dataSource.setIdleTimeout(100);
        dataStoreClient = new DataStoreClient();
        dataStoreClient.setServiceRoute("localhost", testPort);
        edgeDirectorClient = new EdgeDirectorClient();
        edgeDirectorClient.setServiceRoute("localhost", testPort);
        archiveClient = new ArchiveClient();
        archiveClient.setServiceRoute("localhost", testPort);

        edgeDataStore = new EdgeDataStoreDaoImpl(dataSource);
        edgeService = new EdgeDataStoreService(edgeDataStore);


        service = new DataStoreService("127.0.0.1",
                testPort,
                new FileRepository(),
                dataSource,
                edgeService,
                new Initialization(), null
        );

        new EdgeArchiveService("127.0.0.1",
                testPort, Files.createTempDirectory("domainCrawlerTest"), null, Initialization.already(), null);

        String userAgent = "nu.marginalia.wmsa.edge-crawler";
        fetcher = new HttpFetcher(userAgent);

        languageFilter = new LanguageFilter();

        var lm = TestLanguageModels.getLanguageModels();

        var ke = new DocumentKeywordExtractor(new NGramDict(lm));
        var se = new SentenceExtractor(lm);

        DomainCrawlerFactory domainCrawlerFactory =
                new DomainCrawlerFactory(fetcher,
                        new HtmlProcessor(ke,new SentenceExtractor(lm)),
                        new PlainTextProcessor(ke, se), archiveClient, new DomainCrawlerRobotsTxt(fetcher, userAgent), languageFilter, new IpBlockList(new GeoIpBlocklist()));

        workerFactory = new WorkerFactory(domainCrawlerFactory,
                new TaskProvider() {

                    @Override
                    public EdgeIndexTask getIndexTask(int pass) {
                        try {
                            return indexTasks.pop();
                        }
                        catch (NoSuchElementException ex) {
                            return new EdgeIndexTask(null, 0, 0, 1.);
                        }
                    }

                    @Override
                    public EdgeIndexTask getDiscoverTask() {
                        try {
                            return discoverTasks.pop();
                        }
                        catch (NoSuchElementException ex) {
                            return new EdgeIndexTask(null, 0, 0, 1.);
                        }
                    }
                },
                new HttpRedirectResolver(userAgent),
                new UploadFacadeDirectImpl(edgeDataStore,
                        Mockito.mock(EdgeIndexClient.class),
                        edgeDirectorClient),
                /* new UploadFacadeDirectImpl(edgeDataStore, new SearchIndexWriterDummyImpl()) */
                new IpBlockList(new GeoIpBlocklist()));

        RxJavaPlugins.setErrorHandler(ex -> {
            if (ex instanceof UndeliverableException) {
                ex = ex.getCause();
            }
            logger.error("Error {} {}", ex.getClass(), ex.getMessage());
        });

        Spark.get("/edge/task/blocked", (req,rsp) -> "false");
        Spark.awaitInitialization();
    }

    @SneakyThrows
    @AfterAll
    public static void tearDownAll() {
        dataSource.close();
        Spark.awaitStop();
    }


    @BeforeEach
    @SneakyThrows
    public void setUp() {

        edgeDataStore.clearCaches();

        evalScript(dataSource, "sql/data-store-init.sql");
        evalScript(dataSource, "sql/edge-crawler-cache.sql");
    }

    @AfterEach
    @SneakyThrows
    public void tearDown() {
        dataSource.close();
    }

    @Test
    @Disabled
    void localCrawl() throws URISyntaxException {

        dataStoreClient.putUrl(Context.internal(), 0,
                new EdgeUrl("https://www.marginalia.nu/"))
                .blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("http://www.cs.uni.edu/~mccormic/humor.html")).blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("https://www.leonardcohenfiles.com/")).blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("http://atsf.railfan.net/")).blockingSubscribe();
        dataStoreClient.putUrl(Context.internal(), 0, new EdgeUrl("http://sprott.physics.wisc.edu/")).blockingSubscribe();

        final List<LinkedBlockingQueue<WorkerResults>> queues = new ArrayList<>(crawlJobsSpecifications.size());

        for (int i = 0; i < crawlJobsSpecifications.size(); i++) {
            queues.add(new LinkedBlockingQueue<>(1));
        }

        for (int i = 0; i < crawlJobsSpecifications.size()*16; i++) {
            var spec = crawlJobsSpecifications.get(i/16);
            var queue = queues.get(i/16);

            Worker worker;
            if (spec.pass == 0) {
                worker = workerFactory.buildDiscoverWorker(queue);
            }
            else {
                worker = workerFactory.buildIndexWorker(queue, spec.pass);
            }

            new Thread(worker, "Fetcher-"+i).start();
        }

        var uploader = workerFactory.buildUploader(queues);
        uploader.run();
    }

    @SneakyThrows
    @Test
    void testCrawl() {
        EdgeIndexTask task = new EdgeIndexTask(new EdgeDomain("www.marginalia.nu"), 0, 0, 1.);
        task.urls.add(new EdgeUrl("https://www.marginalia.nu/"));
        discoverTasks.add(task);

        LinkedBlockingQueue<WorkerResults> queue = new LinkedBlockingQueue<>();

        var uploader = workerFactory.buildUploader(List.of(queue));
        workerFactory.buildDiscoverWorker(queue).runCycle();
        assertFalse(queue.isEmpty());

        queue.poll().upload(uploader);
    }
}