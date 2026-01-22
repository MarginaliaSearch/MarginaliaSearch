package nu.marginalia;

import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.CompiledSearchFilterSpec;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.converting.writer.ConverterBatchWriter;
import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.fetcher.DomainCookies;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.functions.searchquery.QueryFactory;
import nu.marginalia.index.IndexQueryExecution;
import nu.marginalia.index.StatefulIndex;
import nu.marginalia.index.config.IndexFileName;
import nu.marginalia.index.forward.construction.ForwardIndexConverter;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.reverse.construction.full.FullIndexConstructor;
import nu.marginalia.index.reverse.construction.prio.PrioIndexConstructor;
import nu.marginalia.index.searchset.DomainRankings;
import nu.marginalia.index.searchset.SearchSetAny;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.loading.LoaderIndexJournalWriter;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.loading.documents.DocumentLoaderService;
import nu.marginalia.loading.documents.KeywordLoaderService;
import nu.marginalia.loading.domains.CachingDomainIdRegistry;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.loading.links.DomainLinksLoaderService;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecordFileWriter;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.test.IntegrationTestModule;
import nu.marginalia.test.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class IntegrationTest {
    static {
        System.setProperty("index.disableViabilityPrecheck", "true");
    }

    IntegrationTestModule testModule;
    @Inject
    DomainProcessor domainProcessor;

    @Inject
    DomainLinksLoaderService linksService;
    @Inject
    KeywordLoaderService keywordLoaderService;
    @Inject
    DocumentLoaderService documentLoaderService;

    @Inject
    FileStorageService fileStorageService;

    @Inject
    DomainRankings domainRankings;

    @Inject
    DocumentDbWriter documentDbWriter;
    @Inject
    LoaderIndexJournalWriter journalWriter;
    @Inject
    LanguageConfiguration languageConfiguration;

    Path warcData = null;
    Path crawlDataParquet = null;
    Path processedDataDir = null;

    @Inject
    StatefulIndex statefulIndex;
    @Inject
    DocumentDbReader documentDbReader;

    @Inject
    IndexResultRankingService rankingService;

    @Inject
    QueryFactory queryFactory;

    @BeforeEach
    public void setupTest() throws IOException {
        testModule = new IntegrationTestModule();

        Guice.createInjector(testModule).injectMembers(this);

        warcData = Files.createTempFile("warc", ".warc.gz");
        crawlDataParquet = Files.createTempFile("crawl", ".parquet");
        processedDataDir = Files.createTempDirectory("processed");
    }

    @AfterEach
    public void tearDownTest() throws IOException {
        Files.deleteIfExists(warcData);
        Files.deleteIfExists(crawlDataParquet);
        TestUtil.clearTempDir(processedDataDir);

        testModule.cleanUp();
    }


    @Test
    public void run() throws Exception {

        /** CREATE WARC */
        try (WarcRecorder warcRecorder = new WarcRecorder(warcData)) {
            warcRecorder.writeWarcinfoHeader("127.0.0.1", new EdgeDomain("www.example.com"),
                    new HttpFetcherImpl.DomainProbeResult.Ok(new EdgeUrl("https://www.example.com/")));

            warcRecorder.writeReferenceCopy(new EdgeUrl("https://www.example.com/"),
                    new DomainCookies(),
                    "text/html", 200,
                    """
                            <html>
                            <h1>Hello World</h1>
                            <body>
                            <p>The best description of my problem solving process is the Feynman algorithm, which is sometimes presented as a joke where the hidden subtext is “be smart”, but I disagree. The “algorithm” is a surprisingly lucid description of how thinking works in the context of hard problems where the answer can’t simply be looked up or trivially broken down, iterated upon in a bottom-up fashion, or approached with similar methods.
                               The trick is that there is no trick. This is how thinking works. It appears that when you feed your brain related information, without further active involvement, it starts to digest the information you’ve fed it.
                               </p>
                            </body>
                            </html>
                            """.getBytes(),
                    "",
                    ContentTags.empty()
            );

            warcRecorder.writeReferenceCopy(new EdgeUrl("https://www.example.com/sv"),
                    new DomainCookies(),
                    "text/html", 200,
                    """
                            <html>
                            <head>
                            <meta charset="utf-8"/>
                            </head>
                            <h1>Statens potatismjölsnämd</h1>
                            <body>
                            <p>
                            Härigenom förordnas, att förordningen den 26 juni 1933 örn tillverkning
                            av potatismjöl, vilken förordning jämlikt förordningen den 2 april 1937 (nr
                            108) gäller till och med den 30 september 1940, skall äga fortsatt giltighet till
                            och med den 30 september 1943. 
                            </p>
                            </body>
                            </html>
                            """.getBytes(),
                    "",
                    ContentTags.empty()
            );
        }

        /** CONVERT WARC */
        CrawledDocumentParquetRecordFileWriter.convertWarc(
                "www.example.com",
                new UserAgent("search.marginalia.nu",
                        "search.marginalia.nu"),
                warcData,
                crawlDataParquet);

        /** PROCESS CRAWL DATA */

        var processedDomain = domainProcessor.fullProcessing(SerializableCrawlDataStream.openDataStream(crawlDataParquet));

        System.out.println(processedDomain);

        /** WRITE PROCESSED DATA */

        try (ConverterBatchWriter cbw = new ConverterBatchWriter(processedDataDir, 0)) {
            cbw.writeProcessedDomain(processedDomain);

        }
        // Write a single batch-switch marker in the process log so that the loader will read the data
        Files.writeString(processedDataDir.resolve("processor.log"), "F\n", StandardOpenOption.CREATE_NEW);

        /** LOAD PROCESSED DATA */

        LoaderInputData inputData = new LoaderInputData(List.of(processedDataDir));

        DomainIdRegistry domainIdRegistry = Mockito.mock(CachingDomainIdRegistry.class);
        when(domainIdRegistry.getDomainId(any())).thenReturn(1);

        linksService.loadLinks(domainIdRegistry, new FakeProcessHeartbeat(), inputData);
        keywordLoaderService.loadKeywords(domainIdRegistry, new FakeProcessHeartbeat(), inputData);
        documentLoaderService.loadDocuments(domainIdRegistry, new FakeProcessHeartbeat(), inputData);

        // These must be closed to finalize the associated files
        documentDbWriter.close();
        keywordLoaderService.close();

        /** CONSTRUCT INDEX */

        createForwardIndex();
        createFullReverseIndex();
        createPrioReverseIndex();

        /** SWITCH INDEX */

        statefulIndex.switchIndex();

        // Move the docdb file to the live location
        Files.move(
                IndexLocations.getLinkdbWritePath(fileStorageService).resolve(DOCDB_FILE_NAME),
                IndexLocations.getLinkdbLivePath(fileStorageService).resolve(DOCDB_FILE_NAME)
        );
        // Reconnect the document reader to the new docdb file
        documentDbReader.reconnect();

        /** QUERY */

        try (var indexReference = statefulIndex.get()) {
            var request = RpcQsQuery.newBuilder()
                    .setQueryLimits(RpcQueryLimits.newBuilder()
                            .setTimeoutMs(1000)
                            .setResultsTotal(100)
                            .setResultsByDomain(10)
                            .build())
                    .setLangIsoCode("en")
                    .setHumanQuery("\"is that there is\"")
                    .build();

            var query = queryFactory.createQuery(request, CompiledSearchFilterSpec.builder("test", "test").build(), null);
            System.out.println(query);

            var rs = new IndexQueryExecution(indexReference.get(), rankingService, SearchContext.create(indexReference.get(), new KeywordHasher.AsciiIsh(), query.indexQuery, new SearchSetAny()), 1).run();

            System.out.println(rs);
            Assertions.assertEquals(1, rs.size());
        }

        try (var indexReference = statefulIndex.get()) {
            var request = RpcQsQuery.newBuilder()
                    .setQueryLimits(RpcQueryLimits.newBuilder()
                            .setTimeoutMs(1000)
                            .setResultsTotal(100)
                            .setResultsByDomain(10)
                            .build())
                    .setLangIsoCode("sv")
                    .setHumanQuery("härigenom förordnas")
                    .build();

            var query = queryFactory.createQuery(request, CompiledSearchFilterSpec.builder("test", "test").build(), null);

            System.out.println(query);

            var rs = new IndexQueryExecution(indexReference.get(), rankingService, SearchContext.create(indexReference.get(), new KeywordHasher.AsciiIsh(), query.indexQuery, new SearchSetAny()), 1).run();

            System.out.println(rs);
            Assertions.assertEquals(1, rs.size());
        }
    }


    @Test
    public void testCook() throws Exception {

        /** CREATE WARC */
        try (WarcRecorder warcRecorder = new WarcRecorder(warcData)) {
            warcRecorder.writeWarcinfoHeader("127.0.0.1", new EdgeDomain("www.example.com"),
                    new HttpFetcherImpl.DomainProbeResult.Ok(new EdgeUrl("https://www.example.com/")));

            warcRecorder.writeReferenceCopy(new EdgeUrl("https://www.example.com/cook.html"),
                    new DomainCookies(),
                    "text/html", 200,
                    getClass().getClassLoader().getResourceAsStream("captain-james-cook.html").readAllBytes(),
                    "",
                    ContentTags.empty()
            );
            warcRecorder.writeReferenceCopy(new EdgeUrl("https://www.example.com/brady.html"),
                    new DomainCookies(),
                    "text/html", 200,
                    getClass().getClassLoader().getResourceAsStream("samuel-brady.html").readAllBytes(),
                    "",
                    ContentTags.empty()
            );
            warcRecorder.writeReferenceCopy(new EdgeUrl("https://www.example.com/covey.html"),
                    new DomainCookies(),
                    "text/html", 200,
                    getClass().getClassLoader().getResourceAsStream("james-covey.html").readAllBytes(),
                    "",
                    ContentTags.empty()
            );
        }

        /** CONVERT WARC */
        CrawledDocumentParquetRecordFileWriter.convertWarc(
                "www.example.com",
                new UserAgent("search.marginalia.nu",
                        "search.marginalia.nu"),
                warcData,
                crawlDataParquet);

        /** PROCESS CRAWL DATA */

        var processedDomain = domainProcessor.fullProcessing(SerializableCrawlDataStream.openDataStream(crawlDataParquet));

//        System.out.println(processedDomain);

        /** WRITE PROCESSED DATA */

        try (ConverterBatchWriter cbw = new ConverterBatchWriter(processedDataDir, 0)) {
            cbw.writeProcessedDomain(processedDomain);
        }
        // Write a single batch-switch marker in the process log so that the loader will read the data
        Files.writeString(processedDataDir.resolve("processor.log"), "F\n", StandardOpenOption.CREATE_NEW);

        /** LOAD PROCESSED DATA */

        LoaderInputData inputData = new LoaderInputData(List.of(processedDataDir));

        DomainIdRegistry domainIdRegistry = Mockito.mock(CachingDomainIdRegistry.class);
        when(domainIdRegistry.getDomainId(any())).thenReturn(1);

        linksService.loadLinks(domainIdRegistry, new FakeProcessHeartbeat(), inputData);
        keywordLoaderService.loadKeywords(domainIdRegistry, new FakeProcessHeartbeat(), inputData);
        documentLoaderService.loadDocuments(domainIdRegistry, new FakeProcessHeartbeat(), inputData);

        // These must be closed to finalize the associated files
        documentDbWriter.close();
        keywordLoaderService.close();

        /** CONSTRUCT INDEX */

        createForwardIndex();
        createFullReverseIndex();
        createPrioReverseIndex();

        /** SWITCH INDEX */

        statefulIndex.switchIndex();

        // Move the docdb file to the live location
        Files.move(
                IndexLocations.getLinkdbWritePath(fileStorageService).resolve(DOCDB_FILE_NAME),
                IndexLocations.getLinkdbLivePath(fileStorageService).resolve(DOCDB_FILE_NAME)
        );
        // Reconnect the document reader to the new docdb file
        documentDbReader.reconnect();

        /** QUERY */

        try (var indexReference = statefulIndex.get()) {
            var request = RpcQsQuery.newBuilder()
                    .setQueryLimits(RpcQueryLimits.newBuilder()
                            .setTimeoutMs(10_000_000)
                            .setResultsTotal(100)
                            .setResultsByDomain(10)
                            .build())
                    .setLangIsoCode("en")
                    .setHumanQuery("when was captain james cook born")
                    .build();

            var query = queryFactory.createQuery(request, CompiledSearchFilterSpec.builder("test", "test").build(), null);

//            System.out.println(query);

            var rs = new IndexQueryExecution(indexReference.get(), rankingService, SearchContext.create(indexReference.get(), new KeywordHasher.AsciiIsh(), query.indexQuery, new SearchSetAny()), 1).run();

            System.out.println(rs);

            for (var result : rs) {
                System.out.println(result.getRankingScore() + ": " + result.getUrl());
            }

            Assertions.assertEquals(3, rs.size());
            Assertions.assertEquals("https://www.example.com/cook.html", rs.get(0).getUrl());
        }
    }


    private void createFullReverseIndex() throws IOException {
        Path outputFileDocs = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.FullDocs(), IndexFileName.Version.NEXT);
        Path outputFileDocsValues = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.FullDocsValues(), IndexFileName.Version.NEXT);
        Path outputFilePositions = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.FullPositions(), IndexFileName.Version.NEXT);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        for (String lang : List.of("sv", "en")) {
            Path outputFileWords = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.FullWords(lang), IndexFileName.Version.NEXT);

            if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

            var constructor = new FullIndexConstructor(
                    outputFileDocs,
                    outputFileDocsValues,
                    outputFileWords,
                    outputFilePositions,
                    this::addRankToIdEncoding,
                    tmpDir);

            constructor.createReverseIndex(new FakeProcessHeartbeat(), "createReverseIndexFull", IndexJournal.findJournal(workDir, lang).orElseThrow());
        }
    }

    private void createPrioReverseIndex() throws IOException {

        Path outputFileDocs = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.PrioDocs(), IndexFileName.Version.NEXT);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        for (String lang : List.of("sv", "en")) {
            Path outputFileWords = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.PrioWords(lang), IndexFileName.Version.NEXT);
            var constructor = new PrioIndexConstructor(
                    outputFileDocs,
                    outputFileWords,
                    this::addRankToIdEncoding,
                    tmpDir);

            constructor.createReverseIndex(new FakeProcessHeartbeat(), "createReverseIndexPrio", IndexJournal.findJournal(workDir, lang).orElseThrow(), workDir);
        }
    }

    private void createForwardIndex() throws IOException {

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path outputFileDocsId = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.ForwardDocIds(), IndexFileName.Version.NEXT);
        Path outputFileDocsData = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.ForwardDocData(), IndexFileName.Version.NEXT);
        Path outputFileSpansData = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.ForwardSpansData(), IndexFileName.Version.NEXT);

        ForwardIndexConverter converter = new ForwardIndexConverter(new FakeProcessHeartbeat(),
                outputFileDocsId,
                outputFileDocsData,
                outputFileSpansData,
                IndexJournal.findJournals(workDir, languageConfiguration.languages()).values(),
                domainRankings
        );

        converter.convert();
    }

    private long addRankToIdEncoding(long docId) {
        return UrlIdCodec.addRank(
                255,
                docId);
    }

}
