package nu.marginalia.index.reverse.construction.full;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.reverse.construction.PositionsFileConstructor;
import nu.marginalia.test.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static nu.marginalia.index.reverse.construction.full.TestJournalFactory.EntryData;
import static org.junit.jupiter.api.Assertions.*;

class FullPreindexMergeTest {
    TestJournalFactory journalFactory;
    Path tempDir;
    PositionsFileConstructor positionsFileConstructor;

    MurmurHash3_128 hash = new MurmurHash3_128();

    @BeforeEach
    public void setUp() throws IOException  {
        journalFactory = new TestJournalFactory();
        tempDir = Files.createTempDirectory("merge");
        positionsFileConstructor = new PositionsFileConstructor(tempDir.resolve("positions.dat"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        positionsFileConstructor.close();
        journalFactory.clear();
        TestUtil.clearTempDir(tempDir);
    }

    @Test
    public void testMergeDisjointWords() throws IOException {
        var left = List.of(
                new EntryData(10, 0, 1, 2, 3),
                new EntryData(20, 0, 1, 3));
        var right = List.of(
                new EntryData(30, 0, 4, 5),
                new EntryData(40, 0, 5, 6));

        verifyMerge(left, right);
    }

    @Test
    public void testMergeOverlapping() throws IOException {
        var left = List.of(
                new EntryData(10, 0, 1, 2, 3),
                new EntryData(20, 0, 1, 3),
                new EntryData(30, 0, 2));
        var right = List.of(
                new EntryData(15, 0, 1, 2),
                new EntryData(20, 0, 1, 2, 3),
                new EntryData(50, 0, 3, 4));

        verifyMerge(left, right);
    }

    @Test
    public void testMergeRandomized() throws IOException {
        Random random = new Random(1);

        for (int iteration = 0; iteration < 5; iteration++) {
            verifyMerge(randomEntries(random, 200, 100), randomEntries(random, 300, 100));
        }
    }

    @Test
    public void testMergeOfMergedPreindexes() throws IOException {
        Random random = new Random(2);

        var a = randomEntries(random, 100, 50);
        var b = randomEntries(random, 100, 50);
        var c = randomEntries(random, 100, 50);

        var aPreindex = construct(a);
        var bPreindex = construct(b);
        var ab = FullPreindex.merge(tempDir, aPreindex, bPreindex);
        aPreindex.delete();
        bPreindex.delete();

        var abPreindex = ab.open();
        var cPreindex = construct(c);
        var abc = FullPreindex.merge(tempDir, abPreindex, cPreindex);
        abPreindex.delete();
        cPreindex.delete();

        List<EntryData> all = new ArrayList<>();
        all.addAll(a);
        all.addAll(b);
        all.addAll(c);

        var merged = abc.open();
        assertEquals(expectedSegments(all), actualSegments(merged));
        merged.delete();
    }

    private void verifyMerge(List<EntryData> leftData, List<EntryData> rightData) throws IOException {
        var left = construct(leftData);
        var right = construct(rightData);

        var mergedReference = FullPreindex.merge(tempDir, left, right);
        left.delete();
        right.delete();

        var merged = mergedReference.open();

        List<EntryData> all = new ArrayList<>(leftData);
        all.addAll(rightData);

        assertEquals(expectedSegments(all), actualSegments(merged));
        assertTrue(merged.segments.wordIds.isSorted(0, merged.segments.wordIds.size()));

        merged.delete();
    }

    private FullPreindex construct(List<EntryData> entries) throws IOException {
        var reader = journalFactory.createReader(entries.toArray(EntryData[]::new));
        return FullPreindex.constructPreindex(reader, positionsFileConstructor, DocIdRewriter.identity(), tempDir);
    }

    /** For each word, the sorted distinct document ids that mention it */
    private Map<Long, LongList> expectedSegments(List<EntryData> entries) {
        Map<Long, TreeSet<Long>> docsByWord = new HashMap<>();
        for (var entry : entries) {
            for (String word : entry.wordIds()) {
                docsByWord.computeIfAbsent(hash.hashKeyword(word), k -> new TreeSet<>()).add(entry.docId());
            }
        }

        Map<Long, LongList> ret = new TreeMap<>();
        docsByWord.forEach((word, docs) -> ret.put(word, new LongArrayList(docs)));
        return ret;
    }

    private Map<Long, LongList> actualSegments(FullPreindex preindex) {
        Map<Long, LongList> ret = new TreeMap<>();

        var iter = preindex.segments.iterator(FullPreindexDocuments.RECORD_SIZE_LONGS);
        long expectedStart = 0;
        while (iter.next()) {
            assertEquals(expectedStart, iter.startOffset, "Segments are not contiguous");

            LongList docs = new LongArrayList();
            for (long i = iter.startOffset; i < iter.endOffset; i += FullPreindexDocuments.RECORD_SIZE_LONGS) {
                docs.add(preindex.documents.documents.get(i));
            }
            assertNull(ret.put(iter.wordId, docs), "Duplicate word " + iter.wordId);

            expectedStart = iter.endOffset;
        }

        assertEquals(expectedStart, preindex.documents.size(), "Segments do not cover the documents");

        return ret;
    }

    private List<EntryData> randomEntries(Random random, int numDocs, int numWords) {
        List<EntryData> ret = new ArrayList<>();
        Set<Long> seenDocs = new HashSet<>();

        while (ret.size() < numDocs) {
            long docId = random.nextLong(1, 10_000);
            if (!seenDocs.add(docId))
                continue;

            long[] words = random.longs(random.nextInt(1, 20), 1, numWords).distinct().toArray();
            ret.add(new EntryData(docId, 0, words));
        }

        return ret;
    }
}
