package nu.marginalia.index.construction;

import nu.marginalia.array.LongArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.index.construction.TestJournalFactory.*;
import static org.junit.jupiter.api.Assertions.*;

class ReversePreindexWordSegmentsTest {
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
    public void testWordSegmentsLongWordId() throws IOException {
        var reader = journalFactory.createReader(
                new EntryData(-0xF00BA3L, 0, 1L<<33)
        );

        var segments = ReversePreindexWordSegments.construct(reader, wordsIdFile, countsFile);
        var iter = segments.iterator(1);

        List<TestSegmentData> expected = List.of(
                new TestSegmentData(1L<<33, 0, 1)
        );

        List<TestSegmentData> actual = new ArrayList<>();

        while (iter.next()) {
            actual.add(new TestSegmentData(iter.wordId, iter.startOffset, iter.endOffset));
        }

        assertEquals(expected, actual);
    }
    @Test
    public void testWordSegmentsRepeatedWordId() throws IOException {
        var reader = journalFactory.createReader(
                new EntryData(-0xF00BA3L, 0, 5, 5)
        );

        var segments = ReversePreindexWordSegments.construct(reader, wordsIdFile, countsFile);
        var iter = segments.iterator(1);

        List<TestSegmentData> expected = List.of(
                new TestSegmentData(5, 0, 2)
        );

        List<TestSegmentData> actual = new ArrayList<>();

        while (iter.next()) {
            actual.add(new TestSegmentData(iter.wordId, iter.startOffset, iter.endOffset));
        }

        assertEquals(expected, actual);
    }

    @Test
    public void testWordSegments1() throws IOException {
        var reader = journalFactory.createReader(
                new EntryData(-0xF00BA3L, 0, 10, 40, -100, 33)
        );

        var segments = ReversePreindexWordSegments.construct(reader, wordsIdFile, countsFile);
        var iter = segments.iterator(1);

        List<TestSegmentData> expected = List.of(
                new TestSegmentData(-100, 0, 1),
                new TestSegmentData(10, 1, 2),
                new TestSegmentData(33, 2, 3),
                new TestSegmentData(40, 3, 4)
        );

        List<TestSegmentData> actual = new ArrayList<>();

        while (iter.next()) {
            actual.add(new TestSegmentData(iter.wordId, iter.startOffset, iter.endOffset));
        }

        assertEquals(expected, actual);
    }

    @Test
    public void testWordSegments2() throws IOException {
        var reader = journalFactory.createReader(
                new EntryData(-0xF00BA3L, 0, 10, 40, -100, 33),
                new EntryData(0xF00BA4L, 0, 15, 30, -100, 33)
        );

        var segments = ReversePreindexWordSegments.construct(reader, wordsIdFile, countsFile);
        var iter = segments.iterator(1);

        List<TestSegmentData> expected = List.of(
                new TestSegmentData(-100, 0, 2),
                new TestSegmentData(10, 2, 3),
                new TestSegmentData(15, 3, 4),
                new TestSegmentData(30, 4, 5),
                new TestSegmentData(33, 5, 7),
                new TestSegmentData(40, 7, 8)
        );

        List<TestSegmentData> actual = new ArrayList<>();

        while (iter.next()) {
            actual.add(new TestSegmentData(iter.wordId, iter.startOffset, iter.endOffset));
        }

        assertEquals(expected, actual);
    }


    @Test
    public void testWordSegments_ReadIterator() {
        LongArray wordsArray = LongArray.allocate(4);
        LongArray countsArray = LongArray.allocate(4);
        wordsArray.set(0, -1, -2, -3, -4);
        countsArray.set(0, 2, 1, 3, 5);
        var segments = new ReversePreindexWordSegments(wordsArray, countsArray, null, null);

        var ritr = segments.iterator(1);
        assertTrue(ritr.hasMorePositions());
        assertTrue(ritr.next());
        assertTrue(ritr.isPositionBeforeEnd());
        assertEquals(-1, ritr.wordId);
        assertEquals(0, ritr.idx());
        assertEquals(0, ritr.startOffset);
        assertEquals(2, ritr.endOffset);

        assertTrue(ritr.hasMorePositions());
        assertTrue(ritr.next());
        assertTrue(ritr.isPositionBeforeEnd());
        assertEquals(-2, ritr.wordId);
        assertEquals(1, ritr.idx());
        assertEquals(2, ritr.startOffset);
        assertEquals(3, ritr.endOffset);

        assertTrue(ritr.hasMorePositions());
        assertTrue(ritr.next());
        assertTrue(ritr.isPositionBeforeEnd());
        assertEquals(-3, ritr.wordId);
        assertEquals(2, ritr.idx());
        assertEquals(3, ritr.startOffset);
        assertEquals(6, ritr.endOffset);

        assertTrue(ritr.hasMorePositions());
        assertTrue(ritr.next());
        assertTrue(ritr.isPositionBeforeEnd());
        assertEquals(-4, ritr.wordId);
        assertEquals(3, ritr.idx());
        assertEquals(6, ritr.startOffset);
        assertEquals(11, ritr.endOffset);

        assertFalse(ritr.hasMorePositions());
        assertFalse(ritr.next());
        assertFalse(ritr.isPositionBeforeEnd());

        assertEquals(Long.MIN_VALUE, ritr.wordId);
    }


    @Test
    public void testWordSegments_ConstructionIterator() {
        LongArray wordsArray = LongArray.allocate(4);
        LongArray countsArray = LongArray.allocate(4);
        wordsArray.set(0, -1, -2, -3, -4);
        var segments = new ReversePreindexWordSegments(wordsArray, countsArray, null, null);

        var citr = segments.constructionIterator(1);
        assertEquals(-1, citr.wordId);
        assertEquals(0, citr.idx());
        assertTrue(citr.canPutMore());
        assertTrue(citr.putNext(1));
        assertEquals(1, countsArray.get(0));

        assertEquals(-2, citr.wordId);
        assertEquals(1, citr.idx());
        assertTrue(citr.canPutMore());
        assertTrue(citr.putNext(2));
        assertEquals(2, countsArray.get(1));

        assertEquals(-3, citr.wordId);
        assertEquals(2, citr.idx());
        assertTrue(citr.canPutMore());
        assertTrue(citr.putNext(3));
        assertEquals(3, countsArray.get(2));

        assertEquals(-4, citr.wordId);
        assertEquals(3, citr.idx());
        assertTrue(citr.canPutMore());
        assertFalse(citr.putNext(4));
        assertEquals(4, countsArray.get(3));

        assertEquals(4, citr.idx());
        assertFalse(citr.canPutMore());
        assertEquals(Long.MIN_VALUE, citr.wordId);
    }

}