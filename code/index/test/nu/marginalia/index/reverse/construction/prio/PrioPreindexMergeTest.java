package nu.marginalia.index.reverse.construction.prio;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.reverse.construction.full.TestJournalFactory;
import nu.marginalia.test.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static nu.marginalia.index.reverse.construction.full.TestJournalFactory.EntryDataWithWordMeta;
import static nu.marginalia.index.reverse.construction.full.TestJournalFactory.WordWithMeta;
import static nu.marginalia.index.reverse.construction.full.TestJournalFactory.wm;
import static org.junit.jupiter.api.Assertions.*;

class PrioPreindexMergeTest {
    TestJournalFactory journalFactory;
    Path tempDir;

    MurmurHash3_128 hash = new MurmurHash3_128();

    @BeforeEach
    public void setUp() throws IOException  {
        journalFactory = new TestJournalFactory();
        tempDir = Files.createTempDirectory("merge");
    }

    @AfterEach
    public void tearDown() throws IOException {
        journalFactory.clear();
        TestUtil.clearTempDir(tempDir);
    }

    @Test
    public void testMergeOverlapping() throws IOException {
        var left = List.of(
                new EntryDataWithWordMeta(10, 0, wm(1, 1), wm(2, 1), wm(3, 1)),
                new EntryDataWithWordMeta(20, 0, wm(1, 1), wm(3, 1)),
                new EntryDataWithWordMeta(30, 0, wm(2, 1)));
        var right = List.of(
                new EntryDataWithWordMeta(15, 0, wm(1, 1), wm(2, 1)),
                new EntryDataWithWordMeta(20, 0, wm(1, 1), wm(2, 1), wm(3, 1)),
                new EntryDataWithWordMeta(50, 0, wm(3, 1), wm(4, 1)));

        verifyMerge(left, right);
    }

    @Test
    public void testMergeRandomized() throws IOException {
        Random random = new Random(1);

        for (int iteration = 0; iteration < 5; iteration++) {
            verifyMerge(randomEntries(random, 200, 100), randomEntries(random, 300, 100));
        }
    }

    private void verifyMerge(List<EntryDataWithWordMeta> leftData, List<EntryDataWithWordMeta> rightData) throws IOException {
        var left = construct(leftData);
        var right = construct(rightData);

        var mergedReference = PrioPreindex.merge(tempDir, left, right);
        left.delete();
        right.delete();

        var merged = mergedReference.open();

        List<EntryDataWithWordMeta> all = new ArrayList<>(leftData);
        all.addAll(rightData);

        assertEquals(expectedSegments(all), actualSegments(merged));
        assertTrue(merged.segments.wordIds.isSorted(0, merged.segments.wordIds.size()));

        merged.delete();
    }

    private PrioPreindex construct(List<EntryDataWithWordMeta> entries) throws IOException {
        var reader = journalFactory.createReader(entries.toArray(EntryDataWithWordMeta[]::new));
        return PrioPreindex.constructPreindex(reader, DocIdRewriter.identity(), tempDir);
    }

    /** For each word, the sorted distinct document ids that mention it */
    private Map<Long, LongList> expectedSegments(List<EntryDataWithWordMeta> entries) {
        Map<Long, TreeSet<Long>> docsByWord = new HashMap<>();
        for (var entry : entries) {
            for (WordWithMeta word : entry.wordIds()) {
                docsByWord.computeIfAbsent(hash.hashKeyword(word.wordId()), k -> new TreeSet<>()).add(entry.docId());
            }
        }

        Map<Long, LongList> ret = new TreeMap<>();
        docsByWord.forEach((word, docs) -> ret.put(word, new LongArrayList(docs)));
        return ret;
    }

    private Map<Long, LongList> actualSegments(PrioPreindex preindex) {
        Map<Long, LongList> ret = new TreeMap<>();

        var iter = preindex.segments.iterator(1);
        long expectedStart = 0;
        while (iter.next()) {
            assertEquals(expectedStart, iter.startOffset, "Segments are not contiguous");

            LongList docs = new LongArrayList();
            for (long i = iter.startOffset; i < iter.endOffset; i++) {
                docs.add(preindex.documents.documents.get(i));
            }
            assertNull(ret.put(iter.wordId, docs), "Duplicate word " + iter.wordId);

            expectedStart = iter.endOffset;
        }

        assertEquals(expectedStart, preindex.documents.size(), "Segments do not cover the documents");

        return ret;
    }

    private List<EntryDataWithWordMeta> randomEntries(Random random, int numDocs, int numWords) {
        List<EntryDataWithWordMeta> ret = new ArrayList<>();
        Set<Long> seenDocs = new HashSet<>();

        while (ret.size() < numDocs) {
            long docId = random.nextLong(1, 10_000);
            if (!seenDocs.add(docId))
                continue;

            WordWithMeta[] words = random.longs(random.nextInt(1, 20), 1, numWords)
                    .distinct()
                    .mapToObj(wordId -> wm(wordId, 1))
                    .toArray(WordWithMeta[]::new);

            ret.add(new EntryDataWithWordMeta(docId, 0, words));
        }

        return ret;
    }
}
