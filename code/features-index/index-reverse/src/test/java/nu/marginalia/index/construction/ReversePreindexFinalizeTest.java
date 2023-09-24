
package nu.marginalia.index.construction;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.algo.SortingContext;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.btree.model.BTreeHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static nu.marginalia.index.construction.TestJournalFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReversePreindexFinalizeTest {
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

    @Test
    public void testFinalizeSimple() throws IOException {
        var reader = journalFactory.createReader(new EntryDataWithWordMeta(100, 101, wm(50, 51)));
        var preindex = ReversePreindex.constructPreindex(reader, DocIdRewriter.identity(), tempDir);


        preindex.finalizeIndex(tempDir.resolve( "docs.dat"), tempDir.resolve("words.dat"));
        preindex.delete();

        Path wordsFile = tempDir.resolve("words.dat");
        Path docsFile =  tempDir.resolve("docs.dat");

        assertTrue(Files.exists(wordsFile));
        assertTrue(Files.exists(docsFile));

        System.out.println(Files.size(wordsFile));
        System.out.println(Files.size(docsFile));

        var docsArray = LongArrayFactory.mmapForReadingConfined(docsFile);
        var wordsArray = LongArrayFactory.mmapForReadingConfined(wordsFile);

        var docsHeader = BTreeReader.readHeader(docsArray, 0);
        var wordsHeader = BTreeReader.readHeader(wordsArray, 0);

        assertEquals(1, docsHeader.numEntries());
        assertEquals(1, wordsHeader.numEntries());

        assertEquals(100, docsArray.get(docsHeader.dataOffsetLongs() + 0));
        assertEquals(51, docsArray.get(docsHeader.dataOffsetLongs() + 1));
        assertEquals(50, wordsArray.get(wordsHeader.dataOffsetLongs()));
        assertEquals(0, wordsArray.get(wordsHeader.dataOffsetLongs() + 1));
    }


    @Test
    public void testFinalizeSimple2x2() throws IOException {
        var reader = journalFactory.createReader(
                new EntryDataWithWordMeta(100, 101, wm(50, 51)),
                new EntryDataWithWordMeta(101, 101, wm(51, 52))
                );

        var preindex = ReversePreindex.constructPreindex(reader, DocIdRewriter.identity(), tempDir);

        preindex.finalizeIndex(tempDir.resolve( "docs.dat"), tempDir.resolve("words.dat"));
        preindex.delete();

        Path wordsFile = tempDir.resolve("words.dat");
        Path docsFile =  tempDir.resolve("docs.dat");

        assertTrue(Files.exists(wordsFile));
        assertTrue(Files.exists(docsFile));

        System.out.println(Files.size(wordsFile));
        System.out.println(Files.size(docsFile));

        var docsArray = LongArrayFactory.mmapForReadingConfined(docsFile);
        var wordsArray = LongArrayFactory.mmapForReadingConfined(wordsFile);


        var wordsHeader = BTreeReader.readHeader(wordsArray, 0);

        System.out.println(wordsHeader);

        assertEquals(2, wordsHeader.numEntries());

        long offset1 = wordsArray.get(wordsHeader.dataOffsetLongs() + 1);
        long offset2 = wordsArray.get(wordsHeader.dataOffsetLongs() + 3);

        assertEquals(50, wordsArray.get(wordsHeader.dataOffsetLongs()));
        assertEquals(0, wordsArray.get(wordsHeader.dataOffsetLongs() + 1));
        assertEquals(50, wordsArray.get(wordsHeader.dataOffsetLongs()));
        assertEquals(0, wordsArray.get(wordsHeader.dataOffsetLongs() + 1));

        BTreeHeader docsHeader;

        docsHeader  = BTreeReader.readHeader(docsArray, offset1);
        System.out.println(docsHeader);
        assertEquals(1, docsHeader.numEntries());

        assertEquals(100, docsArray.get(docsHeader.dataOffsetLongs() + 0));
        assertEquals(51, docsArray.get(docsHeader.dataOffsetLongs() + 1));

        docsHeader  = BTreeReader.readHeader(docsArray, offset2);
        System.out.println(docsHeader);
        assertEquals(1, docsHeader.numEntries());

        assertEquals(101, docsArray.get(docsHeader.dataOffsetLongs() + 0));
        assertEquals(52, docsArray.get(docsHeader.dataOffsetLongs() + 1));
    }
}