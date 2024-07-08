package nu.marginalia.index.construction.prio;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.PrioReverseIndexReader;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.construction.full.TestJournalFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.index.construction.full.TestJournalFactory.*;
import static nu.marginalia.index.construction.full.TestJournalFactory.wm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrioPreindexTest {
    Path countsFile;
    Path wordsIdFile;
    Path docsFile;
    Path tempDir;
    Path positionsFile;

    TestJournalFactory journalFactory;

    @BeforeEach
    public void setUp() throws IOException  {
        journalFactory = new TestJournalFactory();

        countsFile = Files.createTempFile("counts", ".dat");
        wordsIdFile = Files.createTempFile("words", ".dat");
        docsFile = Files.createTempFile("docs", ".dat");
        tempDir = Files.createTempDirectory("sort");
        positionsFile = tempDir.resolve("positions.dat");
    }

    @AfterEach
    public void tearDown() throws IOException {
        journalFactory.clear();

        Files.deleteIfExists(countsFile);
        Files.deleteIfExists(wordsIdFile);
        Files.deleteIfExists(positionsFile);
        Files.deleteIfExists(docsFile);

        List<Path> contents = new ArrayList<>();
        Files.list(tempDir).forEach(contents::add);
        for (var tempFile : contents) {
            Files.delete(tempFile);
        }
        Files.delete(tempDir);
    }

    @Test
    public void testFinalizeSimple() throws IOException {
        var journalReader = journalFactory.createReader(
                new EntryDataWithWordMeta(100, 101, wm(50, 51)),
                new EntryDataWithWordMeta(104, 101, wm(50, 52))
        );

        var preindex = PrioPreindex.constructPreindex(journalReader, DocIdRewriter.identity(), tempDir);
        preindex.finalizeIndex(tempDir.resolve( "docs.dat"), tempDir.resolve("words.dat"));
        preindex.delete();

        Path wordsFile = tempDir.resolve("words.dat");
        Path docsFile =  tempDir.resolve("docs.dat");

        assertTrue(Files.exists(wordsFile));
        assertTrue(Files.exists(docsFile));

        var indexReader = new PrioReverseIndexReader("test", wordsFile, docsFile);

        var entrySource = indexReader.documents(50);
        var lqb = new LongQueryBuffer(32);
        entrySource.read(lqb);

        assertEquals(2, lqb.size());
        assertEquals(100, lqb.copyData()[0]);
        assertEquals(104, lqb.copyData()[1]);
    }


    @Test
    public void testFinalizeLargeData() throws IOException {
        EntryDataWithWordMeta[] entries = new EntryDataWithWordMeta[10000];
        for (int i = 0; i < 10000; i++) {
            entries[i] = new EntryDataWithWordMeta(100 + i, 101, wm(50, 51));
        }
        var journalReader = journalFactory.createReader(entries);

        var preindex = PrioPreindex.constructPreindex(journalReader, DocIdRewriter.identity(), tempDir);
        preindex.finalizeIndex(tempDir.resolve( "docs.dat"), tempDir.resolve("words.dat"));
        preindex.delete();

        Path wordsFile = tempDir.resolve("words.dat");
        Path docsFile =  tempDir.resolve("docs.dat");

        assertTrue(Files.exists(wordsFile));
        assertTrue(Files.exists(docsFile));

        var indexReader = new PrioReverseIndexReader("test", wordsFile, docsFile);

        var entrySource = indexReader.documents(50);
        var lqb = new LongQueryBuffer(32);
        entrySource.read(lqb);

        assertEquals(32, lqb.size());
        var dataArray = lqb.copyData();
        for (int i = 0; i < 32; i++) {
            assertEquals(100 + i, dataArray[i]);
        }

        entrySource.read(lqb);
        assertEquals(32, lqb.size());
        dataArray = lqb.copyData();
        for (int i = 0; i < 32; i++) {
            assertEquals(100 + 32 + i, dataArray[i]);
        }
    }
}