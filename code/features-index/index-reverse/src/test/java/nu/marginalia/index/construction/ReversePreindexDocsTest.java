package nu.marginalia.index.construction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static nu.marginalia.index.construction.TestJournalFactory.EntryData;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReversePreindexDocsTest {
    Path countsFile;
    Path wordsIdFile;
    Path docsFile;
    Path tempDir;

    TestJournalFactory journalFactory;

    @BeforeEach
    public void setUp() throws IOException  {
        journalFactory = new TestJournalFactory();

        countsFile = Files.createTempFile("counts", ".dat");
        wordsIdFile = Files.createTempFile("words", ".dat");
        docsFile = Files.createTempFile("docs", ".dat");
        tempDir = Files.createTempDirectory("sort");
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

    @Test
    public void testDocs() throws IOException {
        var reader = journalFactory.createReader(
                new EntryData(-0xF00BA3L, 0, 10, 40, -100, 33)
        );

        var segments = ReversePreindexWordSegments.construct(reader, wordsIdFile, countsFile);
        var docs = ReversePreindexDocuments.construct(docsFile, tempDir, reader, DocIdRewriter.identity(), segments);

        List<TestSegmentData> expected = List.of(
                new TestSegmentData(-100, 0, 2, new long[] { -0xF00BA3L, 0 }),
                new TestSegmentData(10, 2, 4, new long[] { -0xF00BA3L, 0 }),
                new TestSegmentData(33, 4, 6, new long[] { -0xF00BA3L, 0 }),
                new TestSegmentData(40, 6, 8, new long[] { -0xF00BA3L, 0 })
        );

        List<TestSegmentData> actual = new ArrayList<>();

        var iter = segments.iterator(2);
        while (iter.next()) {
            long[] data = new long[(int) (iter.endOffset - iter.startOffset)];
            docs.slice(iter.startOffset, iter.endOffset).get(0, data);
            actual.add(new TestSegmentData(iter.wordId, iter.startOffset, iter.endOffset,
                    data));
        }

        assertEquals(expected, actual);
    }

    @Test
    public void testDocsRepeatedWord() throws IOException {
        var reader = journalFactory.createReader(
                new EntryData(-0xF00BA3L, 0, 4, 4)
        );

        var segments = ReversePreindexWordSegments.construct(reader, wordsIdFile, countsFile);
        var docs = ReversePreindexDocuments.construct(docsFile, tempDir, reader, DocIdRewriter.identity(), segments);

        List<TestSegmentData> expected = List.of(
                new TestSegmentData(4, 0, 4, new long[] { -0xF00BA3L, 0, -0xF00BA3L, 0 })
        );

        List<TestSegmentData> actual = new ArrayList<>();

        var iter = segments.iterator(2);
        while (iter.next()) {
            long[] data = new long[(int) (iter.endOffset - iter.startOffset)];
            docs.slice(iter.startOffset, iter.endOffset).get(0, data);
            actual.add(new TestSegmentData(iter.wordId, iter.startOffset, iter.endOffset,
                    data));
        }

        assertEquals(expected, actual);
    }
    @Test
    public void testDocs2() throws IOException {
        var reader = journalFactory.createReader(
                new EntryData(-0xF00BA3L, 0, 10, 40, -100, 33),
                new EntryData(0xF00BA4L, 0, 15, 30, -100, 33)
        );

        var segments = ReversePreindexWordSegments.construct(reader, wordsIdFile, countsFile);
        var docs = ReversePreindexDocuments.construct(docsFile, tempDir, reader, DocIdRewriter.identity(), segments);

        List<TestSegmentData> expected = List.of(
                new TestSegmentData(-100, 0, 4, new long[] { -0xF00BA3L, 0, 0xF00BA4L, 0 }),
                new TestSegmentData(10, 4, 6, new long[] { -0xF00BA3L, 0}),
                new TestSegmentData(15, 6, 8, new long[] { 0xF00BA4L, 0}),
                new TestSegmentData(30, 8, 10, new long[] { 0xF00BA4L, 0}),
                new TestSegmentData(33, 10, 14, new long[] { -0xF00BA3L, 0, 0xF00BA4L, 0}),
                new TestSegmentData(40, 14, 16, new long[] { -0xF00BA3L, 0})
        );

        List<TestSegmentData> actual = new ArrayList<>();

        var iter = segments.iterator(2);
        while (iter.next()) {
            long[] data = new long[(int) (iter.endOffset - iter.startOffset)];
            docs.slice(iter.startOffset, iter.endOffset).get(0, data);
            actual.add(new TestSegmentData(iter.wordId, iter.startOffset, iter.endOffset,
                    data));
        }
    }

    record TestSegmentData(long wordId, long start, long end, long[] data) {
        public TestSegmentData(long wordId, long start, long end) {
            this(wordId, start, end, null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestSegmentData that = (TestSegmentData) o;

            if (wordId != that.wordId) return false;
            if (start != that.start) return false;
            if (end != that.end) return false;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            int result = (int) (wordId ^ (wordId >>> 32));
            result = 31 * result + (int) (start ^ (start >>> 32));
            result = 31 * result + (int) (end ^ (end >>> 32));
            result = 31 * result + Arrays.hashCode(data);
            return result;
        }

        @Override
        public String toString() {
            return "TestSegmentData{" +
                    "wordId=" + wordId +
                    ", start=" + start +
                    ", end=" + end +
                    ", data=" + Arrays.toString(data) +
                    '}';
        }
    }
}