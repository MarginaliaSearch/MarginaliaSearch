package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.construction.PositionsFileConstructor;
import nu.marginalia.index.construction.ReversePreindex;
import nu.marginalia.index.construction.TestJournalFactory;
import nu.marginalia.index.construction.TestJournalFactory.EntryDataWithWordMeta;
import nu.marginalia.index.positions.PositionsFileReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.index.construction.TestJournalFactory.wm;
import static org.junit.jupiter.api.Assertions.*;

class ReverseIndexReaderTest {
    TestJournalFactory journalFactory;
    Path tempDir;

    @BeforeEach
    public void setUp() throws IOException {
        journalFactory = new TestJournalFactory();

        tempDir = Files.createTempDirectory("sort");
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
                new EntryDataWithWordMeta(100, 101, wm(50, 51, 1, 3, 5))
        );

        assertEquals(1, indexReader.numDocuments(50));

        var positions = indexReader.getTermData(Arena.global(), 50, new long[] { 100 });

        assertEquals(1, positions.length);
        assertNotNull(positions[0]);
        assertEquals((byte) 51, positions[0].flags());
        assertEquals(IntList.of(1, 3, 5), positions[0].positions().values());

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

        assertArrayEquals(new long[] { 100 }, readEntries(indexReader, 50));
        assertArrayEquals(new long[] { 100, 101 }, readEntries(indexReader, 51));
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

        Path posFile = tempDir.resolve("positions.dat");
        Path docsFile = tempDir.resolve("docs.dat");
        Path wordsFile = tempDir.resolve("words.dat");

        try (var positionsFileConstructor = new PositionsFileConstructor(posFile)) {
            var preindex = ReversePreindex.constructPreindex(reader,
                    positionsFileConstructor,
                    DocIdRewriter.identity(), tempDir);
            preindex.finalizeIndex(docsFile, wordsFile);
            preindex.delete();
        }

        return new ReverseIndexReader("test", wordsFile, docsFile, new PositionsFileReader(posFile));

    }
}