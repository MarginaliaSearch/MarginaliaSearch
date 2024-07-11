package nu.marginalia.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.IndexLocations;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.construction.full.FullIndexConstructor;
import nu.marginalia.index.domainrankings.DomainRankings;
import nu.marginalia.index.forward.ForwardIndexConverter;
import nu.marginalia.index.forward.ForwardIndexFileNames;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.index.positions.TermData;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
public class CombinedIndexReaderTest {

    @Inject
    Initialization initialization;

    IndexQueryServiceIntegrationTestModule testModule;

    @Inject
    StatefulIndex statefulIndex;

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

    @Inject
    IndexFactory indexFactory;

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

    private final MockDocumentMeta anyMetadata = new MockDocumentMeta(0, new DocumentMetadata(2, 0, 14, EnumSet.noneOf(DocumentFlags.class)));

    @Test
    public void testSimpleRetrieval() throws Exception {
        new MockData().add(
                d(1, 1),
                anyMetadata,
                w("hello", WordFlags.Title, 33, 55),
                w("world", WordFlags.Subjects, 34)
        ).load();

        var reader = indexFactory.getCombinedIndexReader();
        var query = reader.findFullWord(kw("hello")).build();

        var buffer = new LongQueryBuffer(32);
        query.getMoreResults(buffer);

        assertEquals(
                List.of(d(1, 1)),
                decode(buffer)
        );

        var helloMeta = td(reader, kw("hello"), d(1, 1));
        assertEquals(helloMeta.flags(), WordFlags.Title.asBit());
        assertEquals(IntList.of(33, 55), helloMeta.positions().values());

        var worldMeta = td(reader, kw("world"), d(1, 1));
        assertEquals(worldMeta.flags(), WordFlags.Subjects.asBit());
        assertEquals(IntList.of(34), worldMeta.positions().values());
    }

    TermData td(CombinedIndexReader reader, long wordId, MockDataDocument docId) {
        return (reader.getTermMetadata(Arena.global(), wordId, new CombinedDocIdList(docId.docId())).array())[0];
    }


    @Test
    public void testUnionRetrieval() throws Exception {
        new MockData()
                .add(
                        d(1, 1),
                        anyMetadata,
                        w("hello", WordFlags.Title),
                        w("world", WordFlags.Title)
                )
                .add(
                        d(1, 2),
                        anyMetadata,
                        w("world", WordFlags.Title)
                )
                .add(
                        d(1, 3),
                        anyMetadata,
                        w("world", WordFlags.Title)
                )
                .add(
                        d(2, 4),
                        anyMetadata,
                        w("hello", WordFlags.Title),
                        w("world", WordFlags.Title)
                )
                .load();

        var reader = indexFactory.getCombinedIndexReader();
        var query = reader
                .findFullWord(kw("hello"))
                .also(kw("world"))
                .build();

        var buffer = new LongQueryBuffer(32);
        query.getMoreResults(buffer);

        assertEquals(
                List.of(d(1, 1), d(2, 4)),
                decode(buffer)
        );
    }

    @Test
    public void testNotFilterRetrieval() throws Exception {
        new MockData()
                .add(
                        d(1, 1),
                        anyMetadata,
                        w("hello", WordFlags.Title),
                        w("world", WordFlags.Title),
                        w("goodbye", WordFlags.Title)
                )
                .add(
                        d(1, 2),
                        anyMetadata,
                        w("world", WordFlags.Title)
                )
                .add(
                        d(1, 3),
                        anyMetadata,
                        w("world", WordFlags.Title)
                )
                .add(
                        d(2, 4),
                        anyMetadata,
                        w("hello", WordFlags.Title),
                        w("world", WordFlags.Title)
                )
                .load();

        var reader = indexFactory.getCombinedIndexReader();
        var query = reader.findFullWord(kw("hello"))
                .also(kw("world"))
                .not(kw("goodbye"))
                .build();

        var buffer = new LongQueryBuffer(32);
        query.getMoreResults(buffer);

        assertEquals(
                List.of(d(2, 4)),
                decode(buffer)
        );
    }

    List<MockDataDocument> decode(LongQueryBuffer buffer) {
        List<MockDataDocument> result = new ArrayList<>();
        for (int i = 0; i < buffer.size(); i++) {
            result.add(new MockDataDocument(buffer.data.get(i)));
        }
        return result;
    }

    private MockDataDocument d(int domainId, int ordinal) {
        return new MockDataDocument(domainId, ordinal);
    }

    private void constructIndex() throws IOException {
        createForwardIndex();
        createFullReverseIndex();
        createPrioReverseIndex();
    }

    private void createFullReverseIndex() throws IOException {

        Path outputFileDocs = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.DOCS, ReverseIndexFullFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.WORDS, ReverseIndexFullFileNames.FileVersion.NEXT);
        Path outputFilePositions = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.POSITIONS, ReverseIndexFullFileNames.FileVersion.NEXT);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        var constructor =
                new FullIndexConstructor(
                    outputFileDocs,
                    outputFileWords,
                    outputFilePositions,
                    IndexJournalReader::singleFile,
                    DocIdRewriter.identity(),
                    tmpDir);
        constructor.createReverseIndex(new FakeProcessHeartbeat(), "name", workDir);
    }

    private void createPrioReverseIndex() throws IOException {

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
                IndexJournalReader::singleFile,
                DocIdRewriter.identity(),
                tmpDir);

        constructor.createReverseIndex(new FakeProcessHeartbeat(), "name", workDir);
    }

    private void createForwardIndex() throws IOException {

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

    long kw(String s) {
        return hasher.hashKeyword(s);
    }

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
                var positions = words.stream().map(w -> w.positions).map(pos -> GammaCodedSequence.generate(ByteBuffer.allocate(1024), pos.toIntArray())).toArray(GammaCodedSequence[]::new);

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
        public MockDataDocument(long encodedId) {
            this(UrlIdCodec.getDomainId(encodedId), UrlIdCodec.getDocumentOrdinal(encodedId));
        }

        public long docId() {
            return UrlIdCodec.encodeId(domainId, ordinal);
        }

    }
    record MockDocumentMeta(int features, DocumentMetadata documentMetadata) {}
    record MockDataKeyword(String keyword, long termMetadata, IntList positions) {}

    MockDataKeyword w(String keyword, WordFlags flags, int... positions) {
        return new MockDataKeyword(keyword, new WordMetadata(0L, EnumSet.of(flags)).encode(), IntList.of(positions));

    }
}
