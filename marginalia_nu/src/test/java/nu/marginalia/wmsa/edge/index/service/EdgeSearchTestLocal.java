package nu.marginalia.wmsa.edge.index.service;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.data_store.DataStoreService;
import nu.marginalia.wmsa.data_store.EdgeDataStoreService;
import nu.marginalia.wmsa.data_store.FileRepository;
import nu.marginalia.wmsa.data_store.client.DataStoreClient;
import nu.marginalia.wmsa.edge.assistant.EdgeAssistantService;
import nu.marginalia.wmsa.edge.assistant.client.AssistantClient;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryService;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.assistant.dict.SpellChecker;
import nu.marginalia.wmsa.edge.assistant.eval.MathParser;
import nu.marginalia.wmsa.edge.assistant.eval.Units;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklistImpl;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.search.EdgeSearchOperator;
import nu.marginalia.wmsa.edge.search.EdgeSearchService;
import nu.marginalia.wmsa.edge.search.UnitConversion;
import nu.marginalia.wmsa.edge.search.query.EnglishDictionary;
import nu.marginalia.wmsa.edge.search.query.QueryFactory;
import nu.marginalia.wmsa.edge.search.query.QueryParser;
import nu.marginalia.wmsa.edge.search.results.SearchResultDecorator;
import nu.marginalia.wmsa.edge.search.results.SearchResultValuator;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.nio.file.Files;
import java.nio.file.Path;

import static nu.marginalia.util.TestUtil.getConnection;

@Tag("nobuild")
@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
public class EdgeSearchTestLocal {
    private static HikariDataSource dataSource;
    private static EdgeIndexClient indexClient;
    private static Path tempDir;
    private static EdgeDataStoreDao edgeStoreDao;

    Logger logger = LoggerFactory.getLogger(getClass());
    @SneakyThrows
    public static HikariDataSource provideConnection() {
        return getConnection();
    }

    static int testPort = TestUtil.getPort();

    static Initialization init = new Initialization();
    private QueryParser parser;
    private static NGramDict dict;
    private static LanguageModels lm = new LanguageModels(
            Path.of("/home/vlofgren/Work/ngrams/ngrams-generous-emstr.bin"),
            Path.of("/home/vlofgren/Work/ngrams/tfreq-new-algo3.bin"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin"),
            Path.of("/home/vlofgren/Work/ngrams/English.RDR"),
            Path.of("/home/vlofgren/Work/ngrams/English.DICT"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin")
    );


    @SneakyThrows
    @BeforeAll
    public static void setUpClass() {
        Spark.port(testPort);
        System.setProperty("service-name", "edge-index");
        System.setProperty("unit-test", "TRUE");
        Spark.staticFileLocation("/static/edge/");

        dict = new NGramDict(lm);

        dataSource = new DatabaseModule().provideConnection();
        dataSource.setKeepaliveTime(100);
        dataSource.setIdleTimeout(100);

        indexClient = new EdgeIndexClient();
        indexClient.setServiceRoute("127.0.0.1", ServiceDescriptor.EDGE_INDEX.port);

        AssistantClient assistantClient = new AssistantClient();
        assistantClient.setServiceRoute("127.0.0.1", testPort);

        var dataStoreClient = new DataStoreClient();
        dataStoreClient.setServiceRoute("127.0.0.1", testPort);
        tempDir = Files.createTempDirectory("EdgeIndexClientTest");

        edgeStoreDao = new EdgeDataStoreDaoImpl(dataSource);

        var valuator = new SearchResultValuator(dict);
        EdgeSearchService searchService = new EdgeSearchService("127.0.0.1", testPort,
                edgeStoreDao, indexClient,
                new RendererFactory(), new Initialization(), null,
                dataStoreClient, assistantClient, new UnitConversion(assistantClient),
                new EdgeSearchOperator(assistantClient, edgeStoreDao, indexClient, new QueryFactory(lm, dict, new EnglishDictionary(dict)), new SearchResultDecorator(edgeStoreDao, valuator), valuator),
                new EdgeDomainBlacklistImpl(dataSource), new ScreenshotService(edgeStoreDao));

        EdgeAssistantService assistantService = new EdgeAssistantService("127.0.0.1", testPort, Initialization.already(), null,
                new DictionaryService(dataSource, new SpellChecker()), new MathParser(),
                new Units(new MathParser()), null, null,
                new ScreenshotService(edgeStoreDao), null);

        new DataStoreService("127.0.0.1", testPort, new FileRepository(), dataSource, new EdgeDataStoreService(new EdgeDataStoreDaoImpl(dataSource)), Initialization.already(), null);


        Spark.awaitInitialization();
    }


    @SneakyThrows
    @Test
 //   @Disabled
    public void run() {
        init.setReady();

        System.err.println("http://localhost:"+testPort + "/public/search?query=putty%20ssh%20download%20site:localhost");
        Thread.currentThread().join();
    }

    @AfterAll
    public static void tearDownClass() {
        nu.marginalia.util.test.TestUtil.clearTempDir(tempDir);
    }

}
