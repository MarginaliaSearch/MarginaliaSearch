package nu.marginalia.index.reverse;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.reverse.construction.DocIdRewriter;
import nu.marginalia.index.reverse.construction.PositionsFileConstructor;
import nu.marginalia.index.reverse.construction.full.FullPreindex;
import nu.marginalia.index.reverse.construction.full.TestJournalFactory;
import nu.marginalia.index.reverse.construction.full.TestJournalFactory.EntryDataWithWordMeta;
import nu.marginalia.index.reverse.positions.PositionsFileReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.index.reverse.construction.full.TestJournalFactory.wm;
import static org.junit.jupiter.api.Assertions.*;

class FullReverseIndexReaderTest {
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

    MurmurHash3_128 hash = new MurmurHash3_128();
    long termId(String keyword) {
        return hash.hashKeyword(keyword);
    }

    @Test
    public void testSimple() throws IOException {

        var indexReader = createIndex(
                new EntryDataWithWordMeta(100, 101, wm(50, 51, 1, 3, 5))
        );

        assertEquals(1, indexReader.numDocuments(termId("50")));

        var positions = indexReader.getTermData(Arena.global(), termId("50"), new long[] { 100 });

        assertEquals(1, positions.length);
        assertNotNull(positions[0]);
        assertEquals((byte) 51, positions[0].flags());
        assertEquals(IntList.of(1, 3, 5), positions[0].positions().values());

        assertArrayEquals(new long[] { 100 }, readEntries(indexReader, termId("50")));
    }


    @Test
    public void test2x2() throws IOException {

        var indexReader = createIndex(
                new EntryDataWithWordMeta(100, 101, wm(50, 51), wm(51, 52)),
                new EntryDataWithWordMeta(101, 101, wm(51, 53), wm(52, 54))
        );

        assertEquals(1, indexReader.numDocuments(termId("50")));
        assertEquals(2, indexReader.numDocuments(termId("51")));
        assertEquals(1, indexReader.numDocuments(termId("52")));

        assertArrayEquals(new long[] { 100 }, readEntries(indexReader, termId("50")));
        assertArrayEquals(new long[] { 100, 101 }, readEntries(indexReader, termId("51")));
        assertArrayEquals(new long[] { 101 }, readEntries(indexReader, termId("52")));

    }

    private long[] readEntries(FullReverseIndexReader reader, long wordId) {
        var es = reader.documents(wordId);
        assertTrue(es.hasMore());
        LongQueryBuffer buffer = new LongQueryBuffer(4);
        es.read(buffer);
        assertFalse(es.hasMore());
        return buffer.copyData();
    }

    private FullReverseIndexReader createIndex(EntryDataWithWordMeta... scenario) throws IOException {
        var reader = journalFactory.createReader(scenario);

        Path posFile = tempDir.resolve("positions.dat");
        Path docsFile = tempDir.resolve("docs.dat");
        Path wordsFile = tempDir.resolve("words.dat");

        try (var positionsFileConstructor = new PositionsFileConstructor(posFile)) {
            var preindex = FullPreindex.constructPreindex(reader,
                    positionsFileConstructor,
                    DocIdRewriter.identity(), tempDir);
            preindex.finalizeIndex(docsFile, wordsFile);
            preindex.delete();
        }

        return new FullReverseIndexReader("test", wordsFile, docsFile, new PositionsFileReader(posFile));

    }
}