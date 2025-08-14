package nu.marginalia.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.IndexLocations;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.query.SearchPhraseConstraint;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.construction.full.FullIndexConstructor;
import nu.marginalia.index.construction.prio.PrioIndexConstructor;
import nu.marginalia.index.domainrankings.DomainRankings;
import nu.marginalia.index.forward.ForwardIndexFileNames;
import nu.marginalia.index.forward.construction.ForwardIndexConverter;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalSlopWriter;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.sequence.VarintCodedSequence;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.IntStream;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
public class IndexQueryServiceIntegrationSmokeTest {

    @Inject
    Initialization initialization;

    IndexQueryServiceIntegrationTestModule testModule;

    @Inject
    IndexGrpcService queryService;
    @Inject
    StatefulIndex statefulIndex;

    @Inject
    ServiceHeartbeat heartbeat;

    @Inject
    IndexJournalSlopWriter indexJournalWriter;

    @Inject
    FileStorageService fileStorageService;

    @Inject
    DomainRankings domainRankings;

    @Inject
    DocumentDbReader documentDbReader;

    @Inject
    ProcessHeartbeat processHeartbeat;

    @BeforeEach
    public void setUp() throws IOException {

        testModule = new IndexQueryServiceIntegrationTestModule();
        Guice.createInjector(testModule).injectMembers(this);

        initialization.setReady();
    }

    @AfterEach
    public void tearDown() throws IOException {
        testModule.cleanUp();
    }

    @Test
    public void willItBlend() throws Exception {
        var linkdbWriter = new DocumentDbWriter(
                IndexLocations.getLinkdbLivePath(fileStorageService)
                        .resolve(DOCDB_FILE_NAME)
        );
        for (int i = 1; i < 512; i++) {
            loadData(linkdbWriter, i);
        }
        linkdbWriter.close();
        documentDbReader.reconnect();

        indexJournalWriter.close();
        constructIndex();
        statefulIndex.switchIndex();

        var rsp = queryService.justQuery(
                SearchSpecification.builder()
                        .queryLimits(
                                RpcQueryLimits.newBuilder()
                                        .setResultsByDomain(10)
                                        .setResultsTotal(10)
                                        .setTimeoutMs(Integer.MAX_VALUE)
                                        .setFetchSize(4000)
                                        .build()
                        )
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .rankingParams(PrototypeRankingParameters.sensibleDefaults())
                        .domains(new ArrayList<>())
                        .searchSetIdentifier("NONE")
                        .query(
                            SearchQuery.builder()
                                    .compiledQuery("2 3 5")
                                    .include("3", "5", "2")
                                    .exclude("4")
                                    .build()
                        ).build());

        long[] actual = rsp
                .stream()
                .sorted(Comparator.comparing(RpcDecoratedResultItem::getRankingScore))
                .mapToLong(i -> i.getRawItem().getCombinedId())
                .map(UrlIdCodec::getDocumentOrdinal)
                .toArray();

        System.out.println(Arrays.toString(actual));

        for (long id : actual) {
            Assertions.assertTrue((id % 2) == 0,
                    "Expected all results to contain the factor 2");
            Assertions.assertTrue((id % 3) == 0,
                    "Expected all results to contain the factor 2");
            Assertions.assertTrue((id % 5) == 0,
                    "Expected all results to contain the factor 2");
        }

        Assertions.assertEquals(9, actual.length,
                "Expected 10 results");
        Assertions.assertEquals(9,
                Arrays.stream(actual).boxed().distinct().count(),
                "Results not unique");
    }

    @Test
    public void testSimple() throws Exception {
        var linkdbWriter = new DocumentDbWriter(
                IndexLocations.getLinkdbLivePath(fileStorageService)
                        .resolve(DOCDB_FILE_NAME)
        );
        for (int i = 1; i < 512; i++) {
            loadData(linkdbWriter, i);
        }
        linkdbWriter.close();
        documentDbReader.reconnect();

        indexJournalWriter.close();
        constructIndex();
        statefulIndex.switchIndex();

        var rsp = queryService.justQuery(
                SearchSpecification.builder()
                        .queryLimits(
                                RpcQueryLimits.newBuilder()
                                        .setResultsByDomain(10)
                                        .setResultsTotal(10)
                                        .setTimeoutMs(Integer.MAX_VALUE)
                                        .setFetchSize(4000)
                                        .build()
                        )
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .rankingParams(PrototypeRankingParameters.sensibleDefaults())
                        .domains(new ArrayList<>())
                        .searchSetIdentifier("NONE")
                        .query(
                            SearchQuery.builder()
                                .compiledQuery("2")
                                .include("2")
                                .phraseConstraint(new SearchPhraseConstraint.Full("2"))
                                .build()
                        ).build()
        );

        long[] actual = rsp
                .stream()
                .sorted(Comparator.comparing(RpcDecoratedResultItem::getRankingScore))
                .mapToLong(i -> i.getRawItem().getCombinedId())
                .map(UrlIdCodec::getDocumentOrdinal)
                .toArray();

        System.out.println(Arrays.toString(actual));

        for (long id : actual) {
            Assertions.assertTrue((id % 2) == 0,
                    "Expected all results to contain the factor 2");
        }

        Assertions.assertEquals(10, actual.length,
                "Expected 10 results");
        Assertions.assertEquals(10,
                Arrays.stream(actual).boxed().distinct().count(),
                "Results not unique");
    }

    @Test
    public void testDomainQuery() throws Exception {

        var linkdbWriter = new DocumentDbWriter(
                IndexLocations.getLinkdbLivePath(fileStorageService)
                        .resolve(DOCDB_FILE_NAME)
        );
        for (int i = 1; i < 512; i++) {
            loadDataWithDomain(linkdbWriter, i/100, i);
        }
        linkdbWriter.close();
        documentDbReader.reconnect();

        indexJournalWriter.close();
        constructIndex();
        statefulIndex.switchIndex();

        var rsp = queryService.justQuery(
                SearchSpecification.builder()
                        .queryLimits(
                                RpcQueryLimits.newBuilder()
                                        .setResultsByDomain(10)
                                        .setResultsTotal(10)
                                        .setTimeoutMs(Integer.MAX_VALUE)
                                        .setFetchSize(4000)
                                        .build()
                        )
                        .rankingParams(PrototypeRankingParameters.sensibleDefaults())
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .domains(List.of(2))
                        .query(
                            SearchQuery.builder()
                                .compiledQuery("2 3 5")
                                .include("3", "5", "2")
                                .exclude("4")
                                .phraseConstraint(new SearchPhraseConstraint.Full("2", "3", "5"))
                                .build()
                        ).build());
        long[] ids = new long[] {  210, 270 };
        long[] actual = rsp.stream()
                .sorted(Comparator.comparing(RpcDecoratedResultItem::getRankingScore))
                .mapToLong(i -> i.getRawItem().getCombinedId())
                .map(UrlIdCodec::getDocumentOrdinal)
                .toArray();

        for (long id : actual) {
            System.out.println("Considering " + id);
            Assertions.assertTrue((id % 2) == 0,
                    "Expected all results to contain the factor 2");
            Assertions.assertTrue((id % 3) == 0,
                    "Expected all results to contain the factor 3");
            Assertions.assertTrue((id % 5) == 0,
                    "Expected all results to contain the factor 5");
            Assertions.assertTrue((id/100) == 2);
        }

        Assertions.assertEquals(2, actual.length,
                "Expected 10 results");
        Assertions.assertEquals(2,
                Arrays.stream(actual).boxed().distinct().count(),
                "Results not unique");

        Assertions.assertArrayEquals(ids, actual);
    }

    @Test
    public void testYearQuery() throws Exception {
        var linkdbWriter = new DocumentDbWriter(
                IndexLocations.getLinkdbLivePath(fileStorageService)
                        .resolve(DOCDB_FILE_NAME)
        );
        for (int i = 1; i < 512; i++) {
            loadData(linkdbWriter, i);
        }
        linkdbWriter.close();
        documentDbReader.reconnect();

        indexJournalWriter.close();
        constructIndex();
        statefulIndex.switchIndex();

        var rsp = queryService.justQuery(
                SearchSpecification.builder()
                        .queryLimits(
                                RpcQueryLimits.newBuilder()
                                        .setResultsByDomain(10)
                                        .setResultsTotal(10)
                                        .setTimeoutMs(Integer.MAX_VALUE)
                                        .setFetchSize(4000)
                                        .build()
                        )
                        .year(SpecificationLimit.equals(1998))
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .searchSetIdentifier("NONE")
                        .rankingParams(PrototypeRankingParameters.sensibleDefaults())
                        .query(
                            SearchQuery.builder()
                                .compiledQuery("4")
                                .include("4")
                                .phraseConstraint(new SearchPhraseConstraint.Full("4"))
                                .build()
                        ).build());


        Set<Integer> years = new HashSet<>();

        for (var res : rsp) {
            years.add(DocumentMetadata.decodeYear(res.getRawItem().getEncodedDocMetadata()));
        }

        assertEquals(Set.of(1998), years);
        assertEquals(rsp.size(), 10);

    }


    private void constructIndex() throws SQLException, IOException {
        createForwardIndex();
        createFullReverseIndex();
        createPrioReverseIndex();
    }


    private void createFullReverseIndex() throws SQLException, IOException {

        Path outputFileDocs = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.DOCS, ReverseIndexFullFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.WORDS, ReverseIndexFullFileNames.FileVersion.NEXT);
        Path outputFilePositions = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.POSITIONS, ReverseIndexFullFileNames.FileVersion.NEXT);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        var constructor = new FullIndexConstructor(
                    outputFileDocs,
                    outputFileWords,
                    outputFilePositions,
                    DocIdRewriter.identity(),
                    tmpDir);

        constructor.createReverseIndex(new FakeProcessHeartbeat(), "name", workDir);
    }

    private void createPrioReverseIndex() throws SQLException, IOException {

        Path outputFileDocs = ReverseIndexPrioFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexPrioFileNames.FileIdentifier.DOCS, ReverseIndexPrioFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexPrioFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexPrioFileNames.FileIdentifier.WORDS, ReverseIndexPrioFileNames.FileVersion.NEXT);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        var constructor = new PrioIndexConstructor(
                    outputFileDocs,
                    outputFileWords,
                    DocIdRewriter.identity(),
                    tmpDir);

        constructor.createReverseIndex(new FakeProcessHeartbeat(), "name", workDir);
    }

    private void createForwardIndex() throws SQLException, IOException {

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path outputFileDocsId = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.DOC_ID, ForwardIndexFileNames.FileVersion.NEXT);
        Path outputFileSpansData = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.SPANS_DATA, ForwardIndexFileNames.FileVersion.NEXT);
        Path outputFileDocsData = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.DOC_DATA, ForwardIndexFileNames.FileVersion.NEXT);

        ForwardIndexConverter converter = new ForwardIndexConverter(processHeartbeat,
                outputFileDocsId,
                outputFileDocsData,
                outputFileSpansData,
                IndexJournal.findJournal(workDir).orElseThrow(),
                domainRankings
        );

        converter.convert();
    }

    private long fullId(int id) {
        return UrlIdCodec.encodeId((32 - (id % 32)), id);
    }

    public void loadData(DocumentDbWriter ldbw, int id) throws Exception {
        int[] factors = IntStream
                .rangeClosed(1, id)
                .filter(v -> (id % v) == 0)
                .toArray();

        long fullId = fullId(id);

        ldbw.add(new DocdbUrlDetail(
                fullId, new EdgeUrl("https://www.example.com/"+id),
                "test", "test", 0., "HTML5", 0, null, fullId, 10
        ));

        List<String> keywords = IntStream.of(factors).mapToObj(Integer::toString).toList();
        byte[] metadata = new byte[factors.length];
        for (int i = 0; i < factors.length; i++) {
            metadata[i] = WordFlags.Title.asBit();
        }

        List<VarintCodedSequence> positions = new ArrayList<>();

        ByteBuffer wa = ByteBuffer.allocate(32);
        for (int i = 0; i < factors.length; i++) {
            positions.add(VarintCodedSequence.generate(factors));
        }

        indexJournalWriter.put(fullId,
                new SlopDocumentRecord.KeywordsProjection(
                        "",
                        -1,
                        0,
                        new DocumentMetadata(0, 0, 0, 0, id % 5, id, id % 20, (byte) 0).encode(),
                        100,
                        keywords,
                        metadata,
                        positions,
                        new byte[0],
                        List.of()
                ));

    }

    public void loadDataWithDomain(DocumentDbWriter ldbw, int domain, int id) throws Exception {
        int[] factors = IntStream.rangeClosed(1, id).filter(v -> (id % v) == 0).toArray();
        long fullId = UrlIdCodec.encodeId(domain, id);

        ldbw.add(new DocdbUrlDetail(
                fullId, new EdgeUrl("https://www.example.com/"+id),
                "test", "test", 0., "HTML5", 0, null, id, 10
        ));


        List<String> keywords = IntStream.of(factors).mapToObj(Integer::toString).toList();
        byte[] metadata = new byte[factors.length];
        for (int i = 0; i < factors.length; i++) {
            metadata[i] = WordFlags.Title.asBit();
        }

        List<VarintCodedSequence> positions = new ArrayList<>();

        ByteBuffer wa = ByteBuffer.allocate(32);
        for (int i = 0; i < factors.length; i++) {
            positions.add(VarintCodedSequence.generate(i + 1));
        }

        indexJournalWriter.put(fullId,
                new SlopDocumentRecord.KeywordsProjection(
                        "",
                        -1,
                        0,
                        new DocumentMetadata(0, 0, 0, 0, id % 5, id, id % 20, (byte) 0).encode(),
                        100,
                        keywords,
                        metadata,
                        positions,
                        new byte[0],
                        List.of()
                ));

    }

}
