package nu.marginalia.index;

import nu.marginalia.array.algo.SortingContext;
import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.construction.ReversePreindex;
import nu.marginalia.index.construction.TestJournalFactory;
import nu.marginalia.index.construction.TestJournalFactory.EntryDataWithWordMeta;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.index.construction.TestJournalFactory.wm;
import static org.junit.jupiter.api.Assertions.*;

class ReverseIndexReaderTest {
    TestJournalFactory journalFactory;
    Path tempDir;
    SortingContext sortingContext;

    @BeforeEach
    public void setUp() throws IOException {
        journalFactory = new TestJournalFactory();

        tempDir = Files.createTempDirectory("sort");
        sortingContext = new SortingContext(Path.of("invalid"), 1<<20);
    }

    @AfterEach
    public void tearDown() throws IOException {
        journalFactory.clear();

        List<Path> contents = new ArrayList<>();
        Files.list(tempDir).forEach(contents::add);
        for (var tempFile : contents) {
            Files.delete(tempFile);
        }
        Files.delete(tempDir);
    }

    @Test
    public void testSimple() throws IOException {

        var indexReader = createIndex(
                new EntryDataWithWordMeta(100, 101, wm(50, 51))
        );

        assertEquals(1, indexReader.numDocuments(50));

        long[] meta = indexReader.getTermMeta(50, new long[] { 100 });
        assertArrayEquals(new long[] { 51 }, meta);
        assertArrayEquals(new long[] { 100 }, readEntries(indexReader, 50));
    }

    @Test
    public void test2x2() throws IOException {

        var indexReader = createIndex(
                new EntryDataWithWordMeta(100, 101, wm(50, 51), wm(51, 52)),
                new EntryDataWithWordMeta(101, 101, wm(51, 53), wm(52, 54))

        );

        assertEquals(1, indexReader.numDocuments(50));
        assertEquals(2, indexReader.numDocuments(51));
        assertEquals(1, indexReader.numDocuments(52));

        assertArrayEquals(new long[] { 51 }, indexReader.getTermMeta(50, new long[] { 100 }));
        assertArrayEquals(new long[] { 100 }, readEntries(indexReader, 50));

        assertArrayEquals(new long[] { 52, 53 }, indexReader.getTermMeta(51, new long[] { 100, 101 }));
        assertArrayEquals(new long[] { 100, 101 }, readEntries(indexReader, 51));

        assertArrayEquals(new long[] { 54 }, indexReader.getTermMeta(52, new long[] { 101 }));
        assertArrayEquals(new long[] { 101 }, readEntries(indexReader, 52));

    }

    private long[] readEntries(ReverseIndexReader reader, long wordId) {
        var es = reader.documents(wordId);
        assertTrue(es.hasMore());
        LongQueryBuffer buffer = new LongQueryBuffer(4);
        es.read(buffer);
        assertFalse(es.hasMore());
        return buffer.copyData();
    }

    private ReverseIndexReader createIndex(EntryDataWithWordMeta... scenario) throws IOException {
        var reader = journalFactory.createReader(scenario);
        var preindex = ReversePreindex.constructPreindex(reader, DocIdRewriter.identity(), tempDir, tempDir);


        Path docsFile = tempDir.resolve("docs.dat");
        Path wordsFile = tempDir.resolve("words.dat");

        preindex.finalizeIndex(docsFile, wordsFile);
        preindex.delete();

        return new ReverseIndexReader(wordsFile, docsFile);

    }
}