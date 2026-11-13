package nu.marginalia.index.reverse.construction.full;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.reverse.WordLexicon;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.reverse.positions.PositionCodec;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.sequence.VarintCodedSequence;
import nu.marginalia.skiplist.SkipListConstants;
import nu.marginalia.skiplist.SkipListReader;
import nu.marginalia.skiplist.SkipListValueReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static nu.marginalia.index.reverse.construction.full.TestJournalFactory.EntryData;
import static nu.marginalia.index.reverse.construction.full.TestJournalFactory.EntryDataWithWordMeta;
import static nu.marginalia.index.reverse.construction.full.TestJournalFactory.wm;
import static org.junit.jupiter.api.Assertions.*;

class FullPreindexDocsTest {
    @TempDir
    Path tempDir;

    TestJournalFactory journalFactory;
    MurmurHash3_128 hash = new MurmurHash3_128();
    Path docsFile;
    Path valuesFile;
    Path positionsFile;
    Path workDir;
    String previousRunRecords;
    String previousRunBytes;
    String previousBlockRecords;

    @BeforeEach
    public void setUp() throws IOException {
        previousRunRecords = System.getProperty("index.positionRunRecords");
        previousRunBytes = System.getProperty("index.positionRunBytes");
        previousBlockRecords = System.getProperty("index.fullPreindexBlockRecords");
        journalFactory = new TestJournalFactory();
        docsFile = tempDir.resolve("docs.dat");
        valuesFile = tempDir.resolve("values.dat");
        positionsFile = tempDir.resolve("positions.dat");
        workDir = Files.createDirectory(tempDir.resolve("work"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        restoreProperty("index.positionRunRecords", previousRunRecords);
        restoreProperty("index.positionRunBytes", previousRunBytes);
        restoreProperty("index.fullPreindexBlockRecords", previousBlockRecords);
        journalFactory.clear();
    }

    @Test
    public void testPositionOrder() throws IOException {
        verifyPositionOrder(false);
    }

    @Test
    public void testCompressedOrder() throws IOException {
        System.setProperty("index.fullPreindexBlockRecords", "2");
        verifyPositionOrder(true);
    }

    @Test
    public void testByteLimit() throws IOException {
        System.setProperty("index.positionRunBytes", "65536");

        int[] positions = IntStream.rangeClosed(1, 12000).toArray();
        var entries = new EntryDataWithWordMeta[20];
        List<Posting> firstTerm = new ArrayList<>();
        List<Posting> secondTerm = new ArrayList<>();

        for (int i = 0; i < entries.length; i++) {
            long documentId = entries.length - i;
            entries[i] = new EntryDataWithWordMeta(documentId, 0,
                    wm(10, 2 * i + 1, positions), wm(20, 2 * i + 2, positions));
            firstTerm.addFirst(new Posting(documentId, 2 * i + 1, positions));
            secondTerm.addFirst(new Posting(documentId, 2 * i + 2, positions));
        }

        var expected = Map.of(termId("10"), firstTerm, termId("20"), secondTerm);
        var page = journalFactory.createReader(entries);
        try (var index = TestJournalFactory.constructPreindex(page, DocIdRewriter.identity(), tempDir).open()) {
            assertEquals(expected, readPostings(index));
        }
    }

    @Test
    public void testEmpty() throws IOException {
        var page = journalFactory.createReader(new EntryData[0]);
        try (var preindex = TestJournalFactory.constructPreindex(page, DocIdRewriter.identity(), tempDir).open()) {
            assertEquals(0, preindex.documents.size());
            assertEquals(0, preindex.segments.wordIds.size());
            assertEquals(0, Files.size(preindex.positionsFile));
        }
    }

    @Test
    public void testLanguages() throws IOException {
        System.setProperty("index.positionRunRecords", "100");
        long[] documentIds = LongStream.rangeClosed(1, 800).toArray();

        finalizeLanguage("en", 0, documentIds);
        finalizeLanguage("sv", 1000, documentIds);

        verifyLanguage("en", 0, documentIds);
        verifyLanguage("sv", 1000, documentIds);
    }

    @Test
    public void testConstructFailure() throws IOException {
        System.setProperty("index.positionRunRecords", "1");
        var page = journalFactory.createReader(
                new EntryData(1, 0, 10),
                new EntryData(2, 0, 20),
                new EntryData(3, 0, 30));
        var failure = new IllegalStateException("Failed to rewrite document id");
        DocIdRewriter rewriter = id -> {
            if (id == 3) {
                throw failure;
            }
            return id;
        };

        var exception = assertThrows(IllegalStateException.class,
                () -> constructIndex(page, rewriter, "en"));

        assertSame(failure, exception);
        assertDirectoryEmpty();
    }

    @Test
    public void testFinalizeFailure() throws IOException {
        var page = journalFactory.createReader(new EntryData(1, 0, 10));
        Files.createDirectory(positionsFile);

        assertThrows(IOException.class, () -> constructIndex(page, DocIdRewriter.identity(), "en"));
        assertDirectoryEmpty();
    }

    private void verifyPositionOrder(boolean compressed) throws IOException {
        // Small runs make the ordering check cover spills as well as merging the two pages.
        System.setProperty("index.positionRunRecords", "3");

        var leftPage = journalFactory.createReader(
                new EntryDataWithWordMeta(30, 0, wm(10, 1, 30, 31), wm(20, 2, 32, 33)),
                new EntryDataWithWordMeta(10, 0, wm(10, 3, 10, 11), wm(20, 4, 12, 13)),
                new EntryDataWithWordMeta(20, 0, wm(10, 5, 20, 21), wm(20, 6)));
        var rightPage = journalFactory.createReader(
                new EntryDataWithWordMeta(40, 0, wm(10, 7, 40, 41), wm(20, 8, 42, 43)),
                new EntryDataWithWordMeta(10, 0, wm(10, 9, 999)));

        var expected = Map.of(
                termId("10"), List.of(
                        new Posting(60, 7, 40, 41),
                        new Posting(70, 1, 30, 31),
                        new Posting(80, 5, 20, 21),
                        new Posting(90, 3, 10, 11)),
                termId("20"), List.of(
                        new Posting(60, 8, 42, 43),
                        new Posting(70, 2, 32, 33),
                        new Posting(80, 6),
                        new Posting(90, 4, 12, 13)));

        FullPreindexReference mergedReference;
        try (var left = TestJournalFactory.constructPreindex(leftPage, id -> 100 - id, tempDir, compressed).open();
             var right = TestJournalFactory.constructPreindex(rightPage, id -> 100 - id, tempDir, compressed).open())
        {
            mergedReference = FullPreindex.merge(tempDir, left, right, compressed);
        }

        try (var merged = mergedReference.open()) {
            assertEquals(expected, readPostings(merged));
        }
    }

    private void finalizeLanguage(String language, int positionBase, long[] documentIds) throws IOException {
        var entries = new EntryDataWithWordMeta[documentIds.length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new EntryDataWithWordMeta(documentIds[i], 0,
                    wm(10, 11, positionBase + i + 1), wm(20, 22, positionBase + i + 2));
        }

        var page = journalFactory.createReader(entries);
        constructIndex(page, DocIdRewriter.identity(), language);
        assertDirectoryEmpty();
    }

    private void constructIndex(IndexJournalPage page, DocIdRewriter rewriter, String language) throws IOException {
        var constructor = new FullIndexConstructor(docsFile, valuesFile,
                tempDir.resolve(language + "-words.dat"), positionsFile, rewriter, workDir);
        constructor.createReverseIndex(new FakeProcessHeartbeat(), "test", new IndexJournal(page.baseDir()));
    }

    private void verifyLanguage(String language, int positionBase, long[] documentIds) throws IOException {
        ByteBuffer positionData = ByteBuffer.wrap(Files.readAllBytes(positionsFile));
        var lexicon = new WordLexicon(language, tempDir.resolve(language + "-words.dat"));

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8);
             var valueReader = new SkipListValueReader(valuesFile))
        {
            for (int term = 0; term < 2; term++) {
                String word = term == 0 ? "10" : "20";
                long expectedMetadata = term == 0 ? 11 : 22;
                var reader = new SkipListReader(pool, valueReader, lexicon.wordOffset(termId(word)));
                long[] values = reader.getAllValues(documentIds);

                for (int i = 0; i < documentIds.length; i++) {
                    int expectedPosition = positionBase + i + term + 1;
                    assertEquals(expectedMetadata, values[documentIds.length + i]);
                    assertEquals(IntList.of(expectedPosition), readPositions(positionData, values[i]));
                }
            }
        }
        finally {
            lexicon.close();
        }
    }

    private Map<Long, List<Posting>> readPostings(FullPreindex index) throws IOException {
        ByteBuffer positionData = ByteBuffer.wrap(Files.readAllBytes(index.positionsFile));
        var segments = index.segments.iterator(FullPreindex.RECORD_SIZE_LONGS);
        Map<Long, List<Posting>> result = new HashMap<>();
        long expectedOffset = 0;

        assertTrue(index.segments.wordIds.isSorted(0, index.segments.wordIds.size()));

        while (segments.next()) {
            List<Posting> postings = new ArrayList<>();
            for (long i = segments.startOffset; i < segments.endOffset; i += FullPreindex.RECORD_SIZE_LONGS) {
                long documentId = index.documents.get(i);
                long encodedOffset = index.documents.get(i + 1);
                long metadata = index.documents.get(i + 2);

                assertEquals(expectedOffset, PositionCodec.decodeOffset(encodedOffset),
                        "Positions must be densely packed in posting order");
                postings.add(new Posting(documentId, metadata, readPositions(positionData, encodedOffset)));
                expectedOffset += PositionCodec.decodeSize(encodedOffset);
            }

            assertNull(result.put(segments.wordId, postings), "Duplicate word " + segments.wordId);
        }

        assertEquals(positionData.capacity(), expectedOffset);
        return result;
    }

    private IntList readPositions(ByteBuffer data, long encodedOffset) {
        int offset = (int) PositionCodec.decodeOffset(encodedOffset);
        int size = PositionCodec.decodeSize(encodedOffset);
        return new VarintCodedSequence(data.slice(offset, size)).values();
    }

    private long termId(String word) {
        return hash.hashKeyword(word);
    }

    private void assertDirectoryEmpty() throws IOException {
        try (var files = Files.list(workDir)) {
            assertEquals(List.of(), files.toList());
        }
    }

    private void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        }
        else {
            System.setProperty(name, previousValue);
        }
    }

    private record Posting(long documentId, long metadata, IntList positions) {
        Posting(long documentId, long metadata, int... positions) {
            this(documentId, metadata, IntList.of(positions));
        }
    }
}
