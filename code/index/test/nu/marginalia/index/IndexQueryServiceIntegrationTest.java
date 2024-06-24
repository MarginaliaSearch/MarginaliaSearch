package nu.marginalia.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.IndexLocations;
import nu.marginalia.api.searchquery.model.query.SearchCoherenceConstraint;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.construction.ReverseIndexConstructor;
import nu.marginalia.index.forward.ForwardIndexConverter;
import nu.marginalia.index.forward.ForwardIndexFileNames;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.index.domainrankings.DomainRankings;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.server.Initialization;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import javax.annotation.CheckReturnValue;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
public class IndexQueryServiceIntegrationTest {

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
    IndexJournalWriter indexJournalWriter;

    @Inject
    FileStorageService fileStorageService;

    @Inject
    DomainRankings domainRankings;

    @Inject
    ProcessHeartbeat processHeartbeat;
    @Inject
    DocumentDbReader documentDbReader;

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
    public void testNoPositionsOnlyFlags() throws Exception {
        // Test the case where positions are absent but flags are present

        new MockData().add( // should be included despite no position
                d(1, 1),
                new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                w("hello", WordFlags.Title),
                w("world", WordFlags.Title)
        ).load();

        var query = basicQuery(builder -> builder.query(justInclude("hello", "world")));

        executeSearch(query)
                .expectDocumentsInOrder(d(1,1));
    }


    @Test
    public void testMissingKeywords() throws Exception {
        // Test cases where the user enters search terms that are missing from the lexicon

        new MockData().add(
                d(1, 1),
                new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                w("hello", WordFlags.Title),
                w("world", WordFlags.Title)
        ).load();

        var queryMissingExclude = basicQuery(builder ->
                builder.query(includeAndExclude("hello", "missing")));

        executeSearch(queryMissingExclude)
                .expectDocumentsInOrder(d(1,1));

        var queryMissingInclude = basicQuery(builder ->
                builder.query(justInclude("missing")));

        executeSearch(queryMissingInclude)
                .expectCount(0);

        var queryMissingPriority = basicQuery(builder ->
                builder.query(new SearchQuery(
                        "hello",
                        List.of("hello"),
                        List.of(),
                        List.of(),
                        List.of("missing"),
                        List.of())
                        ));

        executeSearch(queryMissingPriority)
                .expectCount(1);

        var queryMissingAdvice = basicQuery(builder ->
                builder.query(
                        new SearchQuery("hello",
                                List.of("hello"),
                                List.of(),
                                List.of("missing"),
                                List.of(),
                                List.of()
                        )));

        executeSearch(queryMissingAdvice)
                .expectCount(0);

        var queryMissingCoherence = basicQuery(builder ->
                builder.query(
                        new SearchQuery("hello",
                                List.of("hello"),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(SearchCoherenceConstraint.mandatory(List.of("missing", "hello")))
                        )));

        executeSearch(queryMissingCoherence)
                .expectCount(0);
    }

    @Test
    public void testPositions() throws Exception {

        // Test position rules
        new MockData()
            .add( // Case 1: Both words have a position set, should be considered
                d(1, 1),
                new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
            ).add( // Case 2: Only one of the words have a position set, should not be considered
                d(2, 2),
                new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                w("world", new WordMetadata(0L, EnumSet.noneOf(WordFlags.class)).encode())
            ).load();


        var query = basicQuery(builder -> builder.query(justInclude("hello", "world")));

        executeSearch(query)
            .expectDocumentsInOrder(d(1,1));
    }

    @Test
    public void testYear() throws Exception {

        // Test year rules
        new MockData()
                .add( // Case 1: Document is dated 1999
                        d(1, 1),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(1999), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                        w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
                ).add( // Case 2: Document is dated 2000
                        d(2, 2),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2000), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                        w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
                )
                .add( // Case 2: Document is dated 2001
                        d(3, 3),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2001), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                        w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
                )
                .load();


        var beforeY2K = basicQuery(builder ->
                builder.query(justInclude("hello", "world"))
                       .year(SpecificationLimit.lessThan(2000))
        );
        var atY2K = basicQuery(builder ->
                builder.query(justInclude("hello", "world"))
                        .year(SpecificationLimit.equals(2000))
        );
        var afterY2K = basicQuery(builder ->
                builder.query(justInclude("hello", "world"))
                        .year(SpecificationLimit.greaterThan(2000))
        );

        executeSearch(beforeY2K)
                .expectDocumentsInOrder(
                        d(1,1),
                        d(2,2)
                        );
        executeSearch(atY2K)
                .expectDocumentsInOrder(
                        d(2,2)
                );
        executeSearch(afterY2K)
                .expectDocumentsInOrder(
                        d(2,2),
                        d(3,3)
                );
    }

    @Test
    public void testDomain() throws Exception {

        // Test domain filtering
        new MockData()
                // docs from domain 1
                .add(
                        d(1, 1),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(1999), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                        w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
                ).add(
                        d(1, 2),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2000), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                        w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
                )
                // docs from domain 2
                .add(
                        d(2, 1),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2001), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                        w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
                )
                .add(
                        d(2, 2),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2001), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                        w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
                )
                .load();


        var domain1 = basicQuery(builder ->
                builder.query(justInclude("hello", "world"))
                        .domains(List.of(1))
        );
        var domain2 = basicQuery(builder ->
                builder.query(justInclude("hello", "world"))
                        .domains(List.of(2))
        );

        executeSearch(domain1)
                .expectDocumentsInOrder(
                        d(1,1),
                        d(1,2)
                );
        executeSearch(domain2)
                .expectDocumentsInOrder(
                        d(2,1),
                        d(2,2)
                );
    }

    @Test
    public void testExclude() throws Exception {

        // Test exclude rules
        new MockData()
                .add( // Case 1: The required include is present, exclude is absent; should be a result
                        d(1, 1),
                        new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                        w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
                ).add( // Case 2: The required include is present, excluded term is absent; should not be a result
                        d(2, 2),
                        new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                        w("my_darling", new WordMetadata(0L, EnumSet.noneOf(WordFlags.class)).encode())
                ).load();

        var query = basicQuery(builder ->
                builder.query(includeAndExclude("hello", "my_darling"))
        );

        executeSearch(query)
                .expectDocumentsInOrder(d(1,1));
    }

    static class ResultWrapper {
        private final List<MockDataDocument> actual;

        ResultWrapper(List<MockDataDocument> actual) {
            this.actual = actual;
        }

        public ResultWrapper expectDocumentsInOrder(MockDataDocument... expectedDocs) {
            assertEquals(List.of(expectedDocs), actual);

            return this;
        }
        public ResultWrapper expectDocumentInAnyOrder(MockDataDocument... expectedDocs) {
            assertEquals(Set.of(expectedDocs), new HashSet<>(actual));

            return this;
        }
        public ResultWrapper expectCount(int count) {
            assertEquals(count, actual.size());

            return this;
        }
    }

    @CheckReturnValue
    ResultWrapper executeSearch(SearchSpecification searchSpecification) {
        var rsp = queryService.justQuery(searchSpecification);

        List<MockDataDocument> actual = new ArrayList<>();

        System.out.println(rsp);

        for (var result : rsp.results) {
            long docId = result.rawIndexResult.getDocumentId();
            actual.add(new MockDataDocument(UrlIdCodec.getDomainId(docId), UrlIdCodec.getDocumentOrdinal(docId)));
        }

        return new ResultWrapper(actual);
    }


    @Test
    public void testCoherenceRequirement() throws Exception {

        // Test coherence requirement.  Two terms are considered coherent when they
        // appear in the same position
        new MockData()
            .add( // Case 1: Both positions overlap; should be included
                d(1, 1),
                new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                w("world", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode())
            )
            .add( // Case 2: Positions do not overlap, do not include
                d(2, 2),
                new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                w("hello", new WordMetadata(1L, EnumSet.noneOf(WordFlags.class)).encode()),
                w("world", new WordMetadata(2L, EnumSet.noneOf(WordFlags.class)).encode())
            )
        .load();

        var rsp = queryService.justQuery(
                basicQuery(builder -> builder.query(
                        // note coherence requriement
                        includeAndCohere("hello", "world")
                )));

        assertEquals(1, rsp.results.size());
        assertEquals(d(1,1).docId(),
                rsp.results.get(0).rawIndexResult.getDocumentId());
    }

    SearchSpecification basicQuery(Function<SearchSpecification.SearchSpecificationBuilder, SearchSpecification.SearchSpecificationBuilder> mutator)
    {
        var builder = SearchSpecification.builder()
                .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                .queryStrategy(QueryStrategy.SENTENCE)
                .year(SpecificationLimit.none())
                .quality(SpecificationLimit.none())
                .size(SpecificationLimit.none())
                .rank(SpecificationLimit.none())
                .rankingParams(ResultRankingParameters.sensibleDefaults())
                .domains(new ArrayList<>())
                .searchSetIdentifier("NONE");

        return mutator.apply(builder).build();
    }

    SearchQuery justInclude(String... includes) {
        return new SearchQuery(
                Strings.join(List.of(includes), ' '),
                List.of(includes),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    SearchQuery includeAndExclude(List<String> includes, List<String> excludes) {
        return new SearchQuery(
                Strings.join(List.of(includes), ' '),
                includes,
                excludes,
                List.of(),
                List.of(),
                List.of()
        );
    }

    SearchQuery includeAndExclude(String include, String exclude) {
        return new SearchQuery(
                include,
                List.of(include),
                List.of(exclude),
                List.of(),
                List.of(),
                List.of()
        );
    }

    SearchQuery includeAndCohere(String... includes) {
        return new SearchQuery(
                Strings.join(List.of(includes), ' '),
                List.of(includes),
                List.of(),
                List.of(),
                List.of(),
                List.of(SearchCoherenceConstraint.mandatory(List.of(includes)))
        );
    }
    private MockDataDocument d(int domainId, int ordinal) {
        return new MockDataDocument(domainId, ordinal);
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

        var constructor =
                new ReverseIndexConstructor(
                    outputFileDocs,
                    outputFileWords,
                    outputFilePositions,
                    IndexJournalReader::singleFile,
                    DocIdRewriter.identity(),
                    tmpDir);
        constructor.createReverseIndex(new FakeProcessHeartbeat(), "name", workDir);
    }

    private void createPrioReverseIndex() throws SQLException, IOException {

        Path outputFileDocs = ReverseIndexPrioFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexPrioFileNames.FileIdentifier.DOCS, ReverseIndexPrioFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexPrioFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexPrioFileNames.FileIdentifier.WORDS, ReverseIndexPrioFileNames.FileVersion.NEXT);
        Path outputFilePositions = ReverseIndexPrioFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexPrioFileNames.FileIdentifier.POSITIONS, ReverseIndexPrioFileNames.FileVersion.NEXT);
        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        var constructor = new ReverseIndexConstructor(
                outputFileDocs,
                outputFileWords,
                outputFilePositions,
                IndexJournalReader::singleFile,
                DocIdRewriter.identity(),
                tmpDir);

        constructor.createReverseIndex(new FakeProcessHeartbeat(), "name", workDir);
    }

    private void createForwardIndex() throws SQLException, IOException {

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path outputFileDocsId = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.DOC_ID, ForwardIndexFileNames.FileVersion.NEXT);
        Path outputFileDocsData = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.DOC_DATA, ForwardIndexFileNames.FileVersion.NEXT);

        ForwardIndexConverter converter = new ForwardIndexConverter(processHeartbeat,
                IndexJournalReader.paging(workDir),
                outputFileDocsId,
                outputFileDocsData,
                domainRankings
        );

        converter.convert();
    }

    MurmurHash3_128 hasher = new MurmurHash3_128();

    class MockData {
        private final Map<Long, List<MockDataKeyword>> allData = new HashMap<>();
        private final Map<Long, MockDocumentMeta> metaByDoc = new HashMap<>();

        public MockData add(MockDataDocument document,
                        MockDocumentMeta meta,
                        MockDataKeyword... words)
        {
            long id = UrlIdCodec.encodeId(document.domainId, document.ordinal);

            allData.computeIfAbsent(id, l -> new ArrayList<>()).addAll(List.of(words));
            metaByDoc.put(id, meta);

            return this;
        }

        void load() throws IOException, SQLException, URISyntaxException {
            allData.forEach((doc, words) -> {

                var meta = metaByDoc.get(doc);

                var header = new IndexJournalEntryHeader(
                        doc,
                        meta.features,
                        100,
                        meta.documentMetadata.encode()
                );

                String[] keywords = words.stream().map(w -> w.keyword).toArray(String[]::new);
                long[] metadata = words.stream().map(w -> w.termMetadata).mapToLong(Long::longValue).toArray();
                GammaCodedSequence[] positions = new GammaCodedSequence[words.size()]; // FIXME: positions?
                Arrays.setAll(positions, i -> new GammaCodedSequence(ByteBuffer.allocate(1)));

                indexJournalWriter.put(header,
                        new IndexJournalEntryData(keywords, metadata, positions));
            });

            var linkdbWriter = new DocumentDbWriter(
                    IndexLocations.getLinkdbLivePath(fileStorageService).resolve(DOCDB_FILE_NAME)
            );
            for (Long key : allData.keySet()) {
                linkdbWriter.add(new DocdbUrlDetail(
                        key,
                        new EdgeUrl("https://www.example.com"),
                        "test",
                        "test",
                        0.,
                        "HTML5",
                        0,
                        null,
                        0,
                        5
                ));
            }
            linkdbWriter.close();

            indexJournalWriter.close();
            constructIndex();
            documentDbReader.reconnect();
            statefulIndex.switchIndex();
        }
    }

    record MockDataDocument(int domainId, int ordinal) {
        public long docId() {
            return UrlIdCodec.encodeId(domainId, ordinal);
        }

    }
    record MockDocumentMeta(int features, DocumentMetadata documentMetadata) {
        public MockDocumentMeta(int features, long encoded) {
            this(features, new DocumentMetadata(encoded));
        }
    }
    record MockDataKeyword(String keyword, long termMetadata) {}

    public MockDataKeyword w(String keyword, long termMetadata) { return new MockDataKeyword(keyword, termMetadata); }
    public MockDataKeyword w(String keyword) { return new MockDataKeyword(keyword, 0L); }
    public MockDataKeyword w(String keyword, WordFlags flags) { return new MockDataKeyword(keyword, new WordMetadata(0L, EnumSet.of(flags)).encode()); }
}
