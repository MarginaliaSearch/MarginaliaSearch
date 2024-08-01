package nu.marginalia.index.construction.prio;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.PrioReverseIndexReader;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.construction.full.TestJournalFactory;
import nu.marginalia.model.id.UrlIdCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static nu.marginalia.index.construction.full.TestJournalFactory.EntryDataWithWordMeta;
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

    MurmurHash3_128 hash = new MurmurHash3_128();
    long termId(String keyword) {
        return hash.hashKeyword(keyword);
    }

    @Test
    public void testFinalizeSimple() throws IOException {
        var journalReader = journalFactory.createReader(
                new EntryDataWithWordMeta(100, 101, wm(50, 51)),
                new EntryDataWithWordMeta(104, 101, wm(50, 52)),
                new EntryDataWithWordMeta(106, 101, wm(50, 52))
        );

        var preindex = PrioPreindex.constructPreindex(journalReader, DocIdRewriter.identity(), tempDir);
        preindex.finalizeIndex(tempDir.resolve( "docs.dat"), tempDir.resolve("words.dat"));
        preindex.delete();

        Path wordsFile = tempDir.resolve("words.dat");
        Path docsFile =  tempDir.resolve("docs.dat");

        assertTrue(Files.exists(wordsFile));
        assertTrue(Files.exists(docsFile));

        var indexReader = new PrioReverseIndexReader("test", wordsFile, docsFile);

        var entrySource = indexReader.documents(termId("50"));
        var lqb = new LongQueryBuffer(32);
        entrySource.read(lqb);

        assertEquals(3, lqb.size());
        assertEquals(100, lqb.copyData()[0]);
        assertEquals(104, lqb.copyData()[1]);
        assertEquals(106, lqb.copyData()[2]);
    }

    @Test
    public void testFinalizeLargeData() throws IOException {
        int rankComponent = 0;
        int domainComponent = 0;
        int docOrdinal = 0;
        var random = new Random();
        long[] documentIds = new long[10000];

        for (int i = 0; i < documentIds.length; i++) {
            int scenario = random.nextInt(0, 3);

            // Avoid going into scenario 3 when we've already reached max rank
            // instead fall back into scenario 0 as this should be the more common
            // of the two
            if (rankComponent == 63 && scenario == 2) {
                scenario = 0;
            }

            if (scenario == 0) {
                docOrdinal += random.nextInt(1, 100);
            } else if (scenario == 1) {
                domainComponent+=random.nextInt(1, 1000);
                docOrdinal=random.nextInt(0, 10000);
            } else {
                rankComponent = Math.min(63, rankComponent + random.nextInt(1, 2));
                domainComponent=random.nextInt(0, 10000);
                docOrdinal=random.nextInt(0, 10000);
            }

            documentIds[i] = UrlIdCodec.encodeId(rankComponent, domainComponent, docOrdinal);
        }

        EntryDataWithWordMeta[] entries = new EntryDataWithWordMeta[documentIds.length];
        for (int i = 0; i < documentIds.length; i++) {
            entries[i] = new EntryDataWithWordMeta(documentIds[i], 101, wm(50, 51));
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

        int items = indexReader.numDocuments(termId("50"));
        assertEquals(documentIds.length, items);

        var entrySource = indexReader.documents(termId("50"));
        var lqb = new LongQueryBuffer(32);

        for (int pos = 0; pos < documentIds.length;) {
            if (!entrySource.hasMore()) {
                Assertions.fail("Out of data @ " + pos);
            }

            entrySource.read(lqb);

            var dataArray = lqb.copyData();
            for (int i = 0; i < lqb.size(); i++) {

                long currValue = dataArray[i];

                if (documentIds[i + pos] != currValue) {
                    System.out.println("Mismatch at position " + (i + pos));

                    long prevValue = documentIds[i + pos - 1];
                    long expectedValue = documentIds[i + pos];

                    System.out.println("Prev: " + prevValue + " -> " +  UrlIdCodec.getRank(prevValue) + " " + UrlIdCodec.getDomainId(prevValue) + " " + UrlIdCodec.getDocumentOrdinal(prevValue));
                    System.out.println("Curr: " + currValue + " -> " + UrlIdCodec.getRank(currValue) + " " + UrlIdCodec.getDomainId(currValue) + " " + UrlIdCodec.getDocumentOrdinal(currValue));
                    System.out.println("Exp: " + expectedValue + " -> " + UrlIdCodec.getRank(expectedValue) + " " + UrlIdCodec.getDomainId(expectedValue) + " " + UrlIdCodec.getDocumentOrdinal(expectedValue));

                    assertTrue(currValue > prevValue, "Current value is not greater than previous value");

                    Assertions.fail();
                }
            }
            pos += lqb.size();
        }

    }
}