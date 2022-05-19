package nu.marginalia.wmsa.edge.index.service;

import com.opencsv.exceptions.CsvValidationException;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.data_store.DataStoreService;
import nu.marginalia.wmsa.data_store.EdgeDataStoreService;
import nu.marginalia.wmsa.data_store.FileRepository;
import nu.marginalia.wmsa.data_store.client.DataStoreClient;
import nu.marginalia.wmsa.edge.archive.client.ArchiveClient;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryService;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.assistant.dict.SpellChecker;
import nu.marginalia.wmsa.edge.assistant.eval.MathParser;
import nu.marginalia.wmsa.edge.assistant.eval.Units;
import nu.marginalia.wmsa.edge.assistant.EdgeAssistantService;
import nu.marginalia.wmsa.edge.assistant.client.AssistantClient;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import nu.marginalia.wmsa.edge.crawler.domain.DomainCrawler;
import nu.marginalia.wmsa.edge.crawler.domain.DomainCrawlerRobotsTxt;
import nu.marginalia.wmsa.edge.crawler.domain.LinkParser;
import nu.marginalia.wmsa.edge.crawler.domain.language.LanguageFilter;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlFeature;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlProcessor;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpFetcher;
import nu.marginalia.wmsa.edge.crawler.worker.GeoIpBlocklist;
import nu.marginalia.wmsa.edge.crawler.worker.IpBlockList;
import nu.marginalia.wmsa.edge.data.dao.*;
import nu.marginalia.wmsa.edge.data.dao.task.*;
import nu.marginalia.wmsa.edge.index.EdgeIndexService;
import nu.marginalia.wmsa.edge.index.IndexServicesFactory;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.service.query.SearchIndexPartitioner;
import nu.marginalia.util.ParallelPipe;
import nu.marginalia.wmsa.edge.integration.model.BasicDocumentData;
import nu.marginalia.wmsa.edge.integration.stackoverflow.StackOverflowPostProcessor;
import nu.marginalia.wmsa.edge.integration.stackoverflow.StackOverflowPostsReader;
import nu.marginalia.wmsa.edge.integration.stackoverflow.model.StackOverflowPost;
import nu.marginalia.wmsa.edge.integration.wikipedia.WikipediaProcessor;
import nu.marginalia.wmsa.edge.integration.wikipedia.WikipediaReader;
import nu.marginalia.wmsa.edge.integration.wikipedia.model.WikipediaArticle;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.*;
import nu.marginalia.wmsa.edge.search.EdgeSearchOperator;
import nu.marginalia.wmsa.edge.search.EdgeSearchService;
import nu.marginalia.wmsa.edge.search.UnitConversion;
import nu.marginalia.wmsa.edge.search.query.*;
import nu.marginalia.wmsa.edge.search.results.SearchResultDecorator;
import nu.marginalia.wmsa.edge.search.results.SearchResultValuator;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;

import static nu.marginalia.util.TestUtil.evalScript;
import static nu.marginalia.util.TestUtil.getConnection;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
public class EdgeSearchTest {
    private static HikariDataSource dataSource;
    private static EdgeIndexService indexService;
    private static EdgeIndexClient indexClient;
    private static Path tempDir;
    private static EdgeDataStoreDao edgeStoreDao;
    private static SentenceExtractor sentenceExtractor;
    private static DocumentKeywordExtractor documentKeywordExtractor;
    private static StackOverflowPostProcessor stackOverflowPostProcessor;
    private static WikipediaProcessor wikipediaProcessor;
    private static SearchIndexes indexes;

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

        dataSource = provideConnection();
        dataSource.setKeepaliveTime(100);
        dataSource.setIdleTimeout(100);

        indexClient = new EdgeIndexClient();
        indexClient.setServiceRoute("127.0.0.1", testPort);

        AssistantClient assistantClient = new AssistantClient();
        assistantClient.setServiceRoute("127.0.0.1", testPort);

        var dataStoreClient = new DataStoreClient();
        dataStoreClient.setServiceRoute("127.0.0.1", testPort);
        tempDir = Files.createTempDirectory("EdgeIndexClientTest");

        var servicesFactory = new IndexServicesFactory(tempDir,tempDir,tempDir,tempDir,
                "writer-index",
                "writer-dictionary",
                "index-words-read",
                "index-urls-read",
                "index-words-write",
                "index-urls-write",
                1L<<24,
                id->false,
                new SearchIndexPartitioner(null)
                );

        servicesFactory.getDictionaryWriter().noCommit = true;

        edgeStoreDao = new EdgeDataStoreDaoImpl(dataSource);

        sentenceExtractor = new SentenceExtractor(lm);
        documentKeywordExtractor = new DocumentKeywordExtractor(new NGramDict(lm));

        stackOverflowPostProcessor = new StackOverflowPostProcessor(sentenceExtractor, documentKeywordExtractor);
        wikipediaProcessor = new WikipediaProcessor(sentenceExtractor, documentKeywordExtractor);

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

        indexes = new SearchIndexes(servicesFactory, new SearchIndexPartitioner(null));

        indexService = new EdgeIndexService("127.0.0.1",
                testPort,
                init,
                null,
                indexes);

        new DataStoreService("127.0.0.1", testPort, new FileRepository(), dataSource, new EdgeDataStoreService(new EdgeDataStoreDaoImpl(dataSource)), Initialization.already(), null);


        Spark.awaitInitialization();
    }


    @SneakyThrows
    @BeforeEach
    public void clearDb() {
        evalScript(dataSource, "sql/data-store-init.sql");
        evalScript(dataSource, "sql/edge-crawler-cache.sql");
        evalScript(dataSource, "sql/reference-data.sql");

        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.createStatement()) {
                Assertions.assertTrue(stmt.executeUpdate("DELETE FROM EC_URL") >= 0);
                Assertions.assertTrue(stmt.executeUpdate("DELETE FROM EC_DOMAIN_LINK") >= 0);
                Assertions.assertTrue(stmt.executeUpdate("DELETE FROM EC_DOMAIN") >= 0);
            }
        }
    }

    @Test @Disabled
    public void getUrls() throws IOException {
        var doc = Jsoup.parse(new URL("https://search.marginalia.nu/search?query=putty%20ssh%20download"), 1000);

        doc.select(".teknisk a").stream().map(e -> e.attr("href")).forEach(
            href -> {
                try {
                    var path = Path.of("/home/vlofgren/Code/tmp-data/").resolve("url-"+href.hashCode());
                    if (!Files.exists(path)) {
                        var doc2  =Jsoup.parse(new URL(href), 9000);
                        Files.writeString(path, doc2.outerHtml(), StandardOpenOption.CREATE_NEW);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        );
    }



    HtmlProcessor processor = new HtmlProcessor(new DocumentKeywordExtractor(new NGramDict(lm)),new SentenceExtractor(lm));

    @SneakyThrows
    @Test
    public void justLoadUrls() {
        var data = Path.of("/home/vlofgren/Code/tmp-data/");
        for (var file : Objects.requireNonNull(data.toFile().listFiles())) {
            testNgram(file.toPath());
        }
//        cralUrl(new EdgeUrl("https://memex.marginalia.nu/"), 5);
//        loadFile(Path.of("/home/vlofgren/Work/tmp.html"));
    }


    @SneakyThrows
    @Test
    //   @Disabled
    public void runStackOverflow() {
        var data = Path.of("/home/vlofgren/Code/tmp-data/");


        var pipe = new ParallelPipe<StackOverflowPost, BasicDocumentData>("pipe", 32, 5, 2) {
            @Override
            public BasicDocumentData onProcess(StackOverflowPost stackOverflowPost) {
                return stackOverflowPostProcessor.process(stackOverflowPost);
            }

            @Override
            public void onReceive(BasicDocumentData stackOverflowIndexData) {
                loadStackOverflowPost(stackOverflowIndexData);
            }
        };

        var reader = new StackOverflowPostsReader("/mnt/storage/downloads.new/stackexchange/sites/philosophy/Posts.xml",
                new EdgeDomain("philosophy.stackexchange.com"), pipe::accept);
        reader.join();

        init.setReady();
        indexService.initialize();

        while (!indexes.repartition());
        while (!indexes.preconvert());
        while (!indexes.reindexAll());

        System.err.println("http://localhost:"+testPort + "/public/search?query=putty%20ssh%20download%20site:localhost");
        Thread.currentThread().join();
    }

    @SneakyThrows
    @Test
    //   @Disabled
    public void runWikipedia() {
        var data = Path.of("/home/vlofgren/Code/tmp-data/");

        var reader = new WikipediaReader("/home/vlofgren/Work/wikipedia_en_100_nopic_2021-06.zim", new EdgeDomain("encyclopedia.marginalia.nu"),
                this::loadWikipediaPost);
        reader.join();

        init.setReady();
        indexService.initialize();

        while (!indexes.repartition());
        while (!indexes.preconvert());
        while (!indexes.reindexAll());

        System.err.println("http://localhost:"+testPort + "/public/search?query=putty%20ssh%20download%20site:localhost");
        Thread.currentThread().join();
    }

    final LinkParser lp = new LinkParser();


    private void loadStackOverflowPost(BasicDocumentData indexData) {

        var url = indexData.getUrl();

        edgeStoreDao.putUrl(-2, url);
        edgeStoreDao.putUrlVisited(new EdgeUrlVisit(url, indexData.hashCode, -2.,
                indexData.getTitle(),
                indexData.getDescription()
                , "",
                EdgeHtmlStandard.HTML5.toString(),
                1 << HtmlFeature.JS.bit,
                1000, 1000, EdgeUrlState.OK));
        edgeStoreDao.putLink(false, indexData.domainLinks);

        putWords(edgeStoreDao.getDomainId(url.domain).getId(),
                edgeStoreDao.getUrlId(url).getId(),
                -2,
                indexData.words);
    }

    private void loadWikipediaPost(WikipediaArticle post) {

        var indexData = wikipediaProcessor.process(post);

        var url = indexData.getUrl();

        edgeStoreDao.putUrl(-2, url);
        edgeStoreDao.putUrlVisited(new EdgeUrlVisit(url, post.body.hashCode(), -2.,
                indexData.getTitle(),
                indexData.getDescription()
                , "",
                EdgeHtmlStandard.HTML5.toString(),
                1 << HtmlFeature.JS.bit,
                1000, 1000, EdgeUrlState.OK));
        edgeStoreDao.putLink(false, indexData.domainLinks);

        putWords(edgeStoreDao.getDomainId(url.domain).getId(),
                edgeStoreDao.getUrlId(url).getId(),
                -2,
                indexData.words);
    }

    @SneakyThrows
    @Test
 //   @Disabled
    public void run() {
        var data = Path.of("/home/vlofgren/Code/tmp-data/");
        for (var file : Objects.requireNonNull(data.toFile().listFiles())) {
            loadFile(file.toPath());
        }

//        cralUrl(new EdgeUrl("https://search.marginalia.nu/"), 5);
//        cralUrl(new EdgeUrl("https://memex.marginalia.nu/"), 5);

//        loadUrl("https://reddit.marginalia.nu", "/reddit/login.html");

        var conn = getConnection();
        ArrayList<Integer> ids = new ArrayList<>();
        try (var c = conn.getConnection()) {
            var stmt = c.prepareStatement("SELECT ID FROM EC_DOMAIN");

            var rsp = stmt.executeQuery();
            while (rsp.next()) {
                ids.add(rsp.getInt(1));
            }

            for (int i = 0; i < ids.size(); i++) {
                try (var s2 = c.prepareStatement("INSERT INTO EC_DOMAIN_NEIGHBORS(DOMAIN_ID, NEIGHBOR_ID, ADJ_IDX) VALUES (?,?,?)")) {
                    for (int j = 0; j < 15; j++) {
                        s2.setInt(1, ids.get(i));
                        s2.setInt(2, ids.get((int)(ids.size()*Math.random())));
                        s2.setInt(3, j);
                        s2.addBatch();
                    }
                    s2.executeBatch();
                }
            }

        }

        init.setReady();
        indexService.initialize();

        while (!indexes.repartition());
        while (!indexes.preconvert());
        while (!indexes.reindexAll());

        System.err.println("http://localhost:"+testPort + "/public/search?query=putty%20ssh%20download%20site:localhost");
        Thread.currentThread().join();
    }

    @SneakyThrows
    private void loadUrl(String uri) {
        try {
            var doc = Jsoup.parse(new URL(uri), 5000);
            var res = processor.processHtmlPage(new EdgeRawPageContents(new EdgeUrl(new URI(uri)), new EdgeUrl(new URI(uri)), "", null, "", true,
                            LocalDateTime.now().toString()),
                    doc);
            var url = new EdgeUrl(uri);
            edgeStoreDao.putUrl(-2, url);
            edgeStoreDao.putUrlVisited(new EdgeUrlVisit(url, 5, -2.,
                    res.metadata.title,
                    res.metadata.description, "",
                    res.metadata.htmlStandard.toString(),
                    res.metadata.features,
                    0, 0, EdgeUrlState.OK));


            logger.info("LW: {}", res.linkWords);
            putWords(edgeStoreDao.getDomainId(url.domain).getId(),
                    edgeStoreDao.getUrlId(url).getId(),
                    -2,
                    res.words);
        }
        catch (SocketTimeoutException ex) {
            ex.printStackTrace();
        }
    }

    private void cralUrl(EdgeUrl url, int pass) throws CsvValidationException, IOException {
        var fetcher = new HttpFetcher("search.marginalia.nu");
        var ingress = new EdgeIndexTask(url.domain, pass, 100, 1.);
        ingress.urls.add(url);
        DomainCrawler dc = new DomainCrawler(fetcher, null, processor, Mockito.mock(ArchiveClient.class), new DomainCrawlerRobotsTxt(fetcher, "search.marginalia.nu")
                , new LanguageFilter(), ingress , new IpBlockList(new GeoIpBlocklist()));
        System.err.println("Crawling " + url);
        var cr = dc.crawl();
        System.err.println("Crawled " + url);
        cr.pageContents.values().forEach(res -> {
            logger.info("Put URL {} {}%", res.url, 100*Math.exp(res.metadata.quality()));
            edgeStoreDao.putUrl(res.metadata.quality(), res.url);
            edgeStoreDao.putUrlVisited(new EdgeUrlVisit(res.url, res.hash, res.metadata.quality(),
                    res.metadata.title,
                    res.metadata.description, "",
                    res.metadata.htmlStandard.toString(),
                    res.metadata.features, res.metadata.textDistinctWords, res.metadata.totalWords, EdgeUrlState.OK));

            putWords(edgeStoreDao.getDomainId(res.url.domain).getId(),
                    edgeStoreDao.getUrlId(res.url).getId(),
                    -2,
                    res.words);
        });
    }

    private void testNgram(Path path) throws IOException, URISyntaxException {
        var doc = Jsoup.parse(Files.readString(path));
        doc.getElementsByTag("a").remove();
        String text = doc.text();

        var res = processor.processHtmlPage(new EdgeRawPageContents(new EdgeUrl("http://www.example.com/"),new EdgeUrl("http://www.example.com/"),  "", null, "", true,
                        LocalDateTime.now().toString()),
                doc);
        if (null == res) {
            return;
        }
        System.out.println(doc.getElementsByTag("title").text());
        System.out.println(res.metadata.description);
        System.out.println("---");

//        System.out.println(Optional.ofNullable(doc.getElementsByTag("h1")).map(Elements::first).map(Element::text).orElse(""));
//
//        System.out.println(res.words.get(IndexBlock.Topic_Names));
//        System.out.println(res.words.get(IndexBlock.Title_Names));
//        System.out.println(res.words.get(IndexBlock.Body_Names));
//        System.out.println(res.words.get(IndexBlock.TextRank));
//        System.out.println(res.words.get(IndexBlock.Keywords));
//        System.out.println(res.words.get(IndexBlock.Names));
//        System.out.println(res.words.get(IndexBlock.Title));
//        System.out.println(res.words.get(IndexBlock.Topic));
//        System.out.println(res.words.get(IndexBlock.Body));
//
//        var multi = new HashSet<>();
//        var trs = new HashSet<>(res.words.get(IndexBlock.TextRank).words);
//        multi.addAll(Sets.intersection(trs,new HashSet<>(res.words.get(IndexBlock.Names).words)));
//        multi.addAll(Sets.intersection(trs,new HashSet<>(res.words.get(IndexBlock.Title).words)));
//        multi.addAll(Sets.intersection(trs,new HashSet<>(res.words.get(IndexBlock.Topic).words)));
//        var uniq = new HashSet<>(Sets.difference(trs, multi));
//        res.words.get(IndexBlock.Body_Names).words.forEach(multi::remove);
//        res.words.get(IndexBlock.Topic_Names).words.forEach(multi::remove);
//        res.words.get(IndexBlock.Title_Names).words.forEach(multi::remove);
//        System.out.println(multi);
//        System.out.println(uniq);
//        System.out.println("--\n--\n");

    }

    private void loadFile(Path path) throws IOException, URISyntaxException {
        var doc = Jsoup.parse(Files.readString(path));
        var url = new EdgeUrl("http://" + Math.abs(path.hashCode()) + ".example.com/");
        var res = processor.processHtmlPage(new EdgeRawPageContents(url, url, "", null, (int)(Math.random()*255)+"."+(int)(Math.random()*255)+"."+(int)(Math.random()*255), Math.random() > 0.5, LocalDateTime.now().toString()),
                doc);
        if (null == res) {
            System.err.println("*** did not insert " + path);
            return;
        }
        edgeStoreDao.putUrl(-2, url);
        edgeStoreDao.putUrlVisited(new EdgeUrlVisit(url, 5, -2.,
                res.metadata.title,
                res.metadata.description, res.ipAddress,
                res.metadata.htmlStandard.toString(),
                res.metadata.features,
                0, 0, EdgeUrlState.OK));

        logger.info("LW: {}", res.linkWords);
        putWords(edgeStoreDao.getDomainId(url.domain).getId(),
                edgeStoreDao.getUrlId(url).getId(),
                -2,
                res.words
        );

    }
    void putWords(int didx, int idx, double quality, EdgePageWordSet wordsSet) {
        indexClient.putWords(Context.internal(), new EdgeId<>(didx), new EdgeId<>(idx), quality,
                wordsSet, 0).blockingSubscribe();
    }

    @AfterAll
    public static void tearDownClass() {
        nu.marginalia.util.test.TestUtil.clearTempDir(tempDir);
    }

}
