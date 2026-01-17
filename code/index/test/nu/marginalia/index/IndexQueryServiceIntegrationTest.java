package nu.marginalia.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.IndexLocations;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.query.*;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.config.IndexFileName;
import nu.marginalia.index.forward.construction.ForwardIndexConverter;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalSlopWriter;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.reverse.construction.full.FullIndexConstructor;
import nu.marginalia.index.reverse.construction.prio.PrioIndexConstructor;
import nu.marginalia.index.searchset.DomainRankings;
import nu.marginalia.index.searchset.SearchSetsService;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.sequence.VarintCodedSequence;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.storage.FileStorageService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import javax.annotation.CheckReturnValue;
import java.io.IOException;
import java.net.URISyntaxException;
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
    IndexJournalSlopWriter indexJournalWriter;

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

        var query = basicQuery(builder -> builder.setTerms(justInclude("hello", "world")));

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
                builder.setTerms(includeAndExclude("hello", "missing")));

        executeSearch(queryMissingExclude)
                .expectDocumentsInOrder(d(1,1));

        var queryMissingInclude = basicQuery(builder ->
                builder.setTerms(justInclude("missing")));

        executeSearch(queryMissingInclude)
                .expectCount(0);

        var queryMissingPriority = basicQuery(builder ->
                builder.setTerms(
                        RpcQueryTerms.newBuilder()
                                .setCompiledQuery("hello")
                                .addTermsQuery("hello")
                                .addTermsPriority("missing")
                                .addTermsPriorityWeight(1.f)
                                .build()));

        executeSearch(queryMissingPriority)
                .expectCount(1);

        var queryMissingAdvice = basicQuery(builder ->
                builder.setTerms(includeWithCohere(List.of("hello"), List.of("missing")))
        );

        executeSearch(queryMissingAdvice)
                .expectCount(0);

        var queryMissingCoherence = basicQuery(builder ->
                builder.setTerms(includeWithCohere(List.of("hello"), List.of("hello", "missing")))
            );

        executeSearch(queryMissingCoherence)
                .expectCount(0);
    }

    @Test
    public void testYear() throws Exception {

        // Test year rules
        new MockData()
                .add( // Case 1: Document is dated 1999
                        d(1, 1),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(1999), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", EnumSet.allOf(WordFlags.class), 1),
                        w("world", EnumSet.allOf(WordFlags.class), 1)
                ).add( // Case 2: Document is dated 2000
                        d(2, 2),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2000), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", EnumSet.allOf(WordFlags.class), 1),
                        w("world", EnumSet.allOf(WordFlags.class), 1)
                )
                .add( // Case 2: Document is dated 2001
                        d(3, 3),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2001), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", EnumSet.allOf(WordFlags.class), 1),
                        w("world", EnumSet.allOf(WordFlags.class), 1)
                )
                .load();


        var beforeY2K = basicQuery(builder ->
                builder.setTerms(justInclude("hello", "world"))
                       .setYear(RpcSpecLimit.newBuilder().setType(RpcSpecLimit.TYPE.LESS_THAN).setValue(2000))
        );
        var atY2K = basicQuery(builder ->
                builder.setTerms(justInclude("hello", "world"))
                        .setYear(RpcSpecLimit.newBuilder().setType(RpcSpecLimit.TYPE.EQUALS).setValue(2000))
        );
        var afterY2K = basicQuery(builder ->
                builder.setTerms(justInclude("hello", "world"))
                        .setYear(RpcSpecLimit.newBuilder().setType(RpcSpecLimit.TYPE.GREATER_THAN).setValue(2000))
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
                        w("hello", EnumSet.allOf(WordFlags.class), 1),
                        w("world", EnumSet.allOf(WordFlags.class), 1)
                ).add(
                        d(1, 2),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2000), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", EnumSet.allOf(WordFlags.class), 1),
                        w("world", EnumSet.allOf(WordFlags.class), 1)
                )
                // docs from domain 2
                .add(
                        d(2, 1),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2001), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", EnumSet.allOf(WordFlags.class), 1),
                        w("world", EnumSet.allOf(WordFlags.class), 1)
                )
                .add(
                        d(2, 2),
                        new MockDocumentMeta(0, new DocumentMetadata(2, PubDate.toYearByte(2001), 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", EnumSet.allOf(WordFlags.class), 1),
                        w("world", EnumSet.allOf(WordFlags.class), 1)
                )
                .load();


        var domain1 = basicQuery(builder ->
                builder.setTerms(justInclude("hello", "world"))
                        .addRequiredDomainIds(1)
        );
        var domain2 = basicQuery(builder ->
                builder.setTerms(justInclude("hello", "world"))
                        .addRequiredDomainIds(2)
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
                        w("hello", EnumSet.allOf(WordFlags.class), 1),
                        w("world", EnumSet.allOf(WordFlags.class), 1)
                ).add( // Case 2: The required include is present, excluded term is absent; should not be a result
                        d(2, 2),
                        new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                        w("hello", EnumSet.allOf(WordFlags.class), 1),
                        w("my_darling", EnumSet.allOf(WordFlags.class), 1)
                ).load();

        var query = basicQuery(builder ->
                builder.setTerms(includeAndExclude("hello", "my_darling"))
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
    ResultWrapper executeSearch(RpcIndexQuery searchSpecification) {
        var rsp = queryService.justQuery(searchSpecification);

        List<MockDataDocument> actual = new ArrayList<>();

        System.out.println(rsp);

        for (var result : rsp) {
            long docId = result.getRawItem().getCombinedId();
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
                w("hello", EnumSet.allOf(WordFlags.class), 1),
                w("world", EnumSet.allOf(WordFlags.class), 1)
            )
            .add( // Case 2: Positions do not overlap, do not include
                d(2, 2),
                new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class))),
                w("hello", EnumSet.allOf(WordFlags.class), 1),
                w("world", EnumSet.allOf(WordFlags.class), 2)
            )
        .load();

        var rsp = queryService.justQuery(
                basicQuery(builder -> builder.setTerms(
                        // note coherence requriement
                        includeAndCohere("hello", "world")
                )));

        assertEquals(1, rsp.size());
        assertEquals(d(2,2).docId(),
                UrlIdCodec.removeRank(rsp.get(0).getRawItem().getCombinedId()));
    }

    RpcIndexQuery basicQuery(Function<RpcIndexQuery.Builder, RpcIndexQuery.Builder> mutator)
    {
        RpcIndexQuery.Builder builder = RpcIndexQuery.newBuilder()
                .setQueryLimits(
                        RpcQueryLimits.newBuilder()
                                .setResultsByDomain(10)
                                .setResultsTotal(10)
                                .setTimeoutMs(Integer.MAX_VALUE)
                                .build()
                )
                .setLangIsoCode("en");

        return mutator.apply(builder).build();
    }

    RpcQueryTerms justInclude(String... includes) {
        return RpcQueryTerms.newBuilder()
                .setCompiledQuery(StringUtils.join(includes, " "))
                .addAllTermsQuery(List.of(includes)).build();
    }

    RpcQueryTerms includeAndExclude(List<String> includes, List<String> excludes) {
        return RpcQueryTerms.newBuilder()
                .setCompiledQuery(StringUtils.join(includes, " "))
                .addAllTermsQuery(includes)
                .addAllTermsExclude(excludes)
                .build();
    }

    RpcQueryTerms includeAndExclude(String include, String exclude) {
        return RpcQueryTerms.newBuilder()
                .setCompiledQuery(include)
                .addTermsQuery(include)
                .addTermsExclude(exclude)
                .build();
    }

    RpcQueryTerms includeAndCohere(String... includes) {
        return RpcQueryTerms.newBuilder()
                .setCompiledQuery(StringUtils.join(includes, " "))
                .addAllTermsQuery(Arrays.asList(includes))
                .addPhrases(
                        RpcPhrases.newBuilder()
                                .setType(RpcPhrases.TYPE.MANDATORY)
                                .addAllTerms(Arrays.asList(includes))
                                .build()
                )
                .build();
    }

    RpcQueryTerms includeWithCohere(List<String> includes, List<String> coherences) {
        return RpcQueryTerms.newBuilder()
                .setCompiledQuery(StringUtils.join(includes, " "))
                .addAllTermsExclude(includes)
                .addPhrases(
                        RpcPhrases.newBuilder()
                                .setType(RpcPhrases.TYPE.MANDATORY)
                                .addAllTerms(coherences)
                                .build()
                )
                .build();
    }


    private MockDataDocument d(int domainId, int ordinal) {
        return new MockDataDocument(domainId, ordinal);
    }

    private void constructIndex() throws SQLException, IOException {
        createForwardIndex();
        createFullReverseIndex();
        createPrioReverseIndex();
    }


    private void createFullReverseIndex() throws IOException {

        Path outputFileDocs = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.FullDocs(), IndexFileName.Version.NEXT);
        Path outputFileDocsValues = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.FullDocsValues(), IndexFileName.Version.NEXT);
        Path outputFileWords = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.FullWords("en"), IndexFileName.Version.NEXT);
        Path outputFilePositions = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.FullPositions(), IndexFileName.Version.NEXT);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        var constructor = new FullIndexConstructor(
                outputFileDocs,
                outputFileDocsValues,
                outputFileWords,
                outputFilePositions,
                DocIdRewriter.identity(),
                tmpDir);

        constructor.createReverseIndex(new FakeProcessHeartbeat(), "createReverseIndexFull", IndexJournal.findJournal(workDir, "en").orElseThrow());

    }

    private void createPrioReverseIndex() throws IOException {

        Path outputFileDocs = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.PrioDocs(), IndexFileName.Version.NEXT);
        Path outputFileWords = IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), new IndexFileName.PrioWords("en"), IndexFileName.Version.NEXT);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        var constructor = new PrioIndexConstructor(
                outputFileDocs,
                outputFileWords,
                DocIdRewriter.identity(),
                tmpDir);

        constructor.createReverseIndex(new FakeProcessHeartbeat(), "createReverseIndexPrio", IndexJournal.findJournal(workDir, "en").orElseThrow(), workDir);
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
                IndexJournal.findJournal(workDir, "en").stream().toList(),
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
            long id = UrlIdCodec.encodeId(0, document.domainId, document.ordinal);

            domainRankings.updateInUnitTest(document.domainId, (short) 0x01);
            allData.computeIfAbsent(id, l -> new ArrayList<>()).addAll(List.of(words));
            metaByDoc.put(id, meta);

            return this;
        }

        void load() throws IOException, SQLException, URISyntaxException {
            for (Map.Entry<Long, List<MockDataKeyword>> entry : allData.entrySet()) {
                Long doc = entry.getKey();
                List<MockDataKeyword> words = entry.getValue();
                var meta = metaByDoc.get(doc);

                List<String> keywords = words.stream().map(w -> w.keyword).toList();

                long[] metadata = new long[keywords.size()];
                for (int i = 0; i < words.size(); i++) {
                    metadata[i] = (byte) words.get(i).termMetadata;
                }

                List<VarintCodedSequence> positions = new ArrayList<>();
                for (int i = 0; i < words.size(); i++) {
                    positions.add(VarintCodedSequence.generate(words.get(i).positions));
                }

                System.out.println(doc);


                indexJournalWriter.put(doc,
                        new SlopDocumentRecord.KeywordsProjection(
                                "",
                                UrlIdCodec.getDocumentOrdinal(doc),
                                meta.features,
                                meta.documentMetadata.encode(),
                                100,
                                "en",
                                keywords,
                                metadata,
                                positions,
                                new byte[0],
                                List.of()
                        ), new KeywordHasher.AsciiIsh());
            }

            var linkdbWriter = new DocumentDbWriter(
                    IndexLocations.getLinkdbLivePath(fileStorageService).resolve(DOCDB_FILE_NAME)
            );
            for (Long docId : allData.keySet()) {
                linkdbWriter.add(new DocdbUrlDetail(
                        docId,
                        new EdgeUrl("https://www.example.com"),
                        "test",
                        "test",
                        "en",
                        0.,
                        "HTML5",
                        0,
                        null,
                        docId.hashCode(),
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
            return UrlIdCodec.encodeId( domainId, ordinal);
        }

    }
    record MockDocumentMeta(int features, DocumentMetadata documentMetadata) {
        public MockDocumentMeta(int features, long encoded) {
            this(features, new DocumentMetadata(encoded));
        }
    }
    record MockDataKeyword(String keyword, long termMetadata, IntList positions) {}

    public MockDataKeyword w(String keyword, EnumSet<WordFlags> wordFlags, int... positions) {
        return new MockDataKeyword(keyword, WordFlags.encode(wordFlags), IntList.of(positions));
    }
    public MockDataKeyword w(String keyword) { return new MockDataKeyword(keyword, 0L, IntList.of()); }
    public MockDataKeyword w(String keyword, WordFlags flags) { return new MockDataKeyword(keyword, flags.asBit(), IntList.of()); }
}
