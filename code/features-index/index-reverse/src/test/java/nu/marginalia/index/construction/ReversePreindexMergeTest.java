
package nu.marginalia.index.construction;

import nu.marginalia.array.algo.SortingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static nu.marginalia.index.construction.TestJournalFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReversePreindexMergeTest {
    TestJournalFactory journalFactory;
    Path countsFile;
    Path wordsIdFile;
    Path docsFile;
    Path tempDir;
    SortingContext sortingContext;

    @BeforeEach
    public void setUp() throws IOException  {
        journalFactory = new TestJournalFactory();

        countsFile = Files.createTempFile("counts", ".dat");
        wordsIdFile = Files.createTempFile("words", ".dat");
        docsFile = Files.createTempFile("docs", ".dat");
        tempDir = Files.createTempDirectory("sort");
        sortingContext = new SortingContext(Path.of("invalid"), 1<<20);
    }

    @AfterEach
    public void tearDown() throws IOException {
        journalFactory.clear();

        Files.deleteIfExists(countsFile);
        Files.deleteIfExists(wordsIdFile);
        List<Path> contents = new ArrayList<>();
        Files.list(tempDir).forEach(contents::add);
        for (var tempFile : contents) {
            Files.delete(tempFile);
        }
        Files.delete(tempDir);
    }

    public ReversePreindex runMergeScenario(
            List<EntryDataWithWordMeta> leftData,
            List<EntryDataWithWordMeta> rightData
    ) throws IOException {
        var reader1 = journalFactory.createReader(leftData.toArray(EntryDataWithWordMeta[]::new));
        var reader2 = journalFactory.createReader(rightData.toArray(EntryDataWithWordMeta[]::new));

        var left = ReversePreindex.constructPreindex(reader1, tempDir, tempDir);
        var right = ReversePreindex.constructPreindex(reader2, tempDir, tempDir);
        return ReversePreindex.merge(tempDir, left, right);
    }

    private List<TestSegmentData> getData(ReversePreindex merged) {
        var iter = merged.segments.iterator(2);
        List<TestSegmentData> actual = new ArrayList<>();
        while (iter.next()) {
            long[] data = new long[(int) (iter.endOffset - iter.startOffset)];
            merged.documents.slice(iter.startOffset, iter.endOffset).get(0, data);
            actual.add(new TestSegmentData(iter.wordId, iter.startOffset, iter.endOffset,
                    data));
        }
        return actual;
    }

    @Test
    public void testDocsMergeSingleNoOverlap() throws IOException {

        IdSequence docIds = new IdSequence();
        IdSequence docMetas = new IdSequence();
        IdSequence wordMetas = new IdSequence();
        IdSequence wordIds = new IdSequence();

        var leftSequence = List.of(new EntryDataWithWordMeta(docIds.nextUnique(), docMetas.nextUnique(), wm(wordIds.nextUnique(), wordMetas.nextUnique())));
        var rightSequence = List.of(new EntryDataWithWordMeta(docIds.nextUnique(), docMetas.nextUnique(), wm(wordIds.nextUnique(), wordMetas.nextUnique())));

        var merged = runMergeScenario(
                leftSequence,
                rightSequence
        );

        var actual = getData(merged);

        var expected = simulateMerge(leftSequence, rightSequence);

        System.out.println(actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testDocsMergeSingleOnlyOverlap() throws IOException {

        IdSequence docIds = new IdSequence();
        IdSequence docMetas = new IdSequence();
        IdSequence wordMetas = new IdSequence();
        IdSequence wordIds = new IdSequence();

        var leftSequence = List.of(new EntryDataWithWordMeta(docIds.nextUnique(), docMetas.nextUnique(), wm(wordIds.nextUnique(), wordMetas.nextUnique())));
        var rightSequence = List.of(new EntryDataWithWordMeta(docIds.nextUnique(), docMetas.nextUnique(), wm(wordIds.alreadySeenSameSequence(), wordMetas.nextUnique())));

        var merged = runMergeScenario(
                leftSequence,
                rightSequence
        );

        var actual = getData(merged);

        var expected = simulateMerge(leftSequence, rightSequence);

        System.out.println(actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testDocsMergeSingleOnlyOverlap2() throws IOException {

        long wid1 = 1;
        long wid2 = 2;
        IdSequence docIds = new IdSequence();
        IdSequence docMetas = new IdSequence();
        IdSequence wordMetas = new IdSequence();

        var leftSequence = List.of(new EntryDataWithWordMeta(docIds.nextUnique(), docMetas.nextUnique(),
                wm(wid1, wordMetas.nextUnique()),
                wm(wid2, wordMetas.nextUnique())
                ));
        var rightSequence = List.of(new EntryDataWithWordMeta(docIds.nextUnique(), docMetas.nextUnique(),
                wm(wid1, wordMetas.nextUnique()),
                wm(wid2, wordMetas.nextUnique())
        ));

        var merged = runMergeScenario(
                leftSequence,
                rightSequence
        );

        var actual = getData(merged);

        var expected = simulateMerge(leftSequence, rightSequence);

        System.out.println(actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testBadCase1() throws IOException {
        long wordId = 0xF00F00BA3L;

        List<EntryDataWithWordMeta> leftSequence = List.of(new EntryDataWithWordMeta(40, 50,
                wm(wordId, 5))
        );
        List<EntryDataWithWordMeta> rightSequence = List.of(new EntryDataWithWordMeta(41, 51,
                wm(wordId, 3),
                wm(wordId, 4))
        );

        var mergedLR = runMergeScenario(
                leftSequence,
                rightSequence
        );
        var mergedRL = runMergeScenario(
                rightSequence,
                leftSequence
        );

        var actualLR = getData(mergedLR);
        var actualRL = getData(mergedRL);

        var expected = simulateMerge(leftSequence, rightSequence);

        assertEquals(actualLR, actualRL);

        if (!expected.equals(actualLR)) {
            System.out.println("*fail*");
            System.out.println(leftSequence);
            System.out.println(rightSequence);
        }
        else {
            System.out.println("*pass*");
        }

        assertEquals(expected, actualLR);

    }

    @Test
    public void testBadCase2() throws IOException {
        long wordId = 100;

        List<EntryDataWithWordMeta> leftSequence = List.of(
                new EntryDataWithWordMeta(1, 50, wm(wordId, 5)),
                new EntryDataWithWordMeta(2, 50, wm(wordId, 5))

        );
        List<EntryDataWithWordMeta> rightSequence = List.of(
                new EntryDataWithWordMeta(3, 50, wm(wordId, 5))
        );

        var mergedLR = runMergeScenario(
                leftSequence,
                rightSequence
        );
        var mergedRL = runMergeScenario(
                rightSequence,
                leftSequence
        );

        var actualLR = getData(mergedLR);
        var actualRL = getData(mergedRL);

        var expected = simulateMerge(leftSequence, rightSequence);

        assertEquals(actualLR, actualRL);

        if (!expected.equals(actualLR)) {
            System.out.println("*fail*");
            System.out.println(leftSequence);
            System.out.println(rightSequence);
        }
        else {
            System.out.println("*pass*");
        }

        assertEquals(expected, actualLR);

    }

    @Test
    public void testFuzz() throws IOException {
        Random r = new Random();
        int maxDocs = 150;
        int maxWords = 160;
        int nIters = 1000;

        for (int i = 0; i < nIters; i++) {
            int nLeft = 1 + r.nextInt(maxDocs);
            int nRight = 1 + r.nextInt(maxDocs);

            IdSequence docIdsLeft = new IdSequence();
            IdSequence docIdsRight = new IdSequence();
            IdSequence docMetas = new IdSequence();
            IdSequence wordMetas = new IdSequence();
            IdSequence wordIds = new IdSequence();

            List<EntryDataWithWordMeta> leftSequence = new ArrayList<>(nLeft);
            for (int j = 0; j < nLeft; j++) {
                WordWithMeta[] words = new WordWithMeta[maxWords == 1 ? 1 : r.nextInt(1, maxWords)];
                Arrays.setAll(words, idx -> {
                    long wordId = wordIds.seenWithP(1.0);
                    long wordMeta = wordMetas.nextUniqueAssociatedWithKey(wordId);
                    return wm(wordId, wordMeta);
                });

                long docId = docIdsLeft.nextUnique();
                long docMeta = docMetas.nextUniqueAssociatedWithKey(docId);
                leftSequence.add(new EntryDataWithWordMeta(docId, docMeta, words));
            }

            List<EntryDataWithWordMeta> rightSequence = new ArrayList<>(nLeft);
            for (int j = 0; j < nRight; j++) {
                WordWithMeta[] words = new WordWithMeta[maxWords == 1 ? 1 : r.nextInt(1, maxWords)];
                Arrays.setAll(words, idx -> {
                    long wordId = wordIds.seenWithP(1.0);
                    long wordMeta = wordMetas.nextUniqueAssociatedWithKey(wordId);
                    return wm(wordId, wordMeta);
                });

                long docId = docIdsRight.seenWithP(docIdsLeft, 0.1);
                long docMeta = docMetas.nextUniqueAssociatedWithKey(docId);
                rightSequence.add(new EntryDataWithWordMeta(docId, docMeta, words));
            }

            var mergedLR = runMergeScenario(
                    leftSequence,
                    rightSequence
            );
            var mergedRL = runMergeScenario(
                    rightSequence,
                    leftSequence
            );

            var actualLR = getData(mergedLR);
            var actualRL = getData(mergedRL);

            var expected = simulateMerge(leftSequence, rightSequence);

            assertEquals(actualLR, actualRL);

            if (!expected.equals(actualLR)) {
                System.out.println("*fail*");
                System.out.println(leftSequence);
                System.out.println(rightSequence);
            }
            else {
                System.out.println("*pass*");
            }

            assertEquals(expected, actualLR);

        }
    }


    public List<TestSegmentData> simulateMerge(
            Collection<EntryDataWithWordMeta> leftInputs,
            Collection<EntryDataWithWordMeta> rightInputs
            ) {
        TreeMap<Long, List<DocWithMeta>> wordToDocs = new TreeMap<>();

        for (var entry : leftInputs) {
            for (var wm : entry.wordIds()) {
                wordToDocs.computeIfAbsent(wm.wordId(), w -> new ArrayList<>()).add(
                        new DocWithMeta(entry.docId(), wm.meta())
                );
            }
        }
        for (var entry : rightInputs) {
            for (var wm : entry.wordIds()) {
                wordToDocs.computeIfAbsent(wm.wordId(), w -> new ArrayList<>()).add(
                        new DocWithMeta(entry.docId(), wm.meta())
                );
            }
        }

        List<TestSegmentData> ret = new ArrayList<>();
        int[] start = new int[1];
        wordToDocs.forEach((wordId, docsList) -> {
            docsList.sort(Comparator.naturalOrder());
            var iter = docsList.iterator();
            DocWithMeta prevVal = null;
            DocWithMeta currentVal;
            while (iter.hasNext()) {
                currentVal = iter.next();
                if (prevVal != null) {
                    if (currentVal.docId == prevVal.docId) {
                        iter.remove();
                    }
                }
                prevVal = currentVal;

            }
            long[] data = new long[docsList.size()*2];
            for (int i = 0; i < docsList.size(); i++) {
                data[2*i] = docsList.get(i).docId;
                data[2*i + 1] = docsList.get(i).meta;
            }
            ret.add(new TestSegmentData(wordId, start[0], start[0] + data.length, data));

            start[0] += data.length;
        });
        return ret;
    }


    record DocWithMeta(long docId, long meta) implements Comparable<DocWithMeta> {

        @Override
        public int compareTo(DocWithMeta o) {
            return Long.compare(docId, o.docId);
        }
    }

    class IdSequence {
        Set<Long> seen = new HashSet<>();
        Map<Long, Long> associatedValues = new HashMap<>();
        private Random random = new Random();

        /** Return alreadySeen() with probability p,
         * else nextUnique()
         */
        public long seenWithP(double p) {
            if (isEmpty() || random.nextDouble() > p)
                return nextUnique();

            return alreadySeenSameSequence();
        }

        public long seenWithP(IdSequence other, double p) {
            if (isEmpty() || random.nextDouble() > p)
                return nextUnique();

            return alreadySeenOtherSequence(other);
        }

        public long nextUnique() {
            for (;;) {
                long val = random.nextLong();
                if (seen.add(val)) {
                    return val;
                }
            }
        }

        public long nextUniqueAssociatedWithKey(long key) {
            return associatedValues.computeIfAbsent(key, k -> nextUnique());
        }

        public long alreadySeenSameSequence() {
            long[] values = seen.stream().mapToLong(Long::longValue).toArray();
            int idx = random.nextInt(0, values.length);
            return values[idx];
        }

        public long alreadySeenOtherSequence(IdSequence other) {
            List<Long> values = new ArrayList<>(other.seen);
            Collections.shuffle(values);
            for (Long maybe : values) {
                if (seen.add(maybe))
                    return maybe;
            }
            return nextUnique();
        }

        public boolean isEmpty() {
            return seen.isEmpty();
        }
    }

}