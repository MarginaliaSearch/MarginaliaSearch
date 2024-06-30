package nu.marginalia.index.journal;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.model.IndexJournalEntryTermData;
import nu.marginalia.index.journal.reader.IndexJournalReaderPagingImpl;
import nu.marginalia.index.journal.writer.IndexJournalWriterSingleFileImpl;
import nu.marginalia.index.journal.reader.IndexJournalReaderSingleFile;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.sequence.GammaCodedSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class IndexJournalWriterTest {
    Path tempFile;
    Path tempFile2;
    ByteBuffer workArea = ByteBuffer.allocate(1024);

    @BeforeEach
    public void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".dat");
        tempFile2 = Files.createTempFile(getClass().getSimpleName(), ".dat");
    }
    @AfterEach
    public void tearDown() throws IOException {
        Files.delete(tempFile);
        Files.delete(tempFile2);
    }

    private GammaCodedSequence gcs(int... values) {
        return GammaCodedSequence.generate(workArea, values);
    }

    static MurmurHash3_128 hasher = new MurmurHash3_128();
    static long wordId(String str) {
        return hasher.hashKeyword(str);
    }

    @Test
    public void testSingleFile() {
        try (var writer = new IndexJournalWriterSingleFileImpl(tempFile)) {
            // Write two documents with two terms each
            writer.put(new IndexJournalEntryHeader(11, 22, 10, 33),
                    new IndexJournalEntryData(
                        new String[]{"word1", "word2"},
                        new long[]{44, 55},
                        new GammaCodedSequence[]{
                                gcs(1, 3, 5),
                                gcs(2, 4, 6),
                        })
                    );
            writer.put(new IndexJournalEntryHeader(12, 23, 11, 34),
                    new IndexJournalEntryData(
                        new String[]{"word1", "word2"},
                        new long[]{45, 56},
                        new GammaCodedSequence[]{
                                gcs(2, 4, 6),
                                gcs(3, 5, 7),
                        })
                    );
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }

        // Read the journal back

        try {
            var reader = new IndexJournalReaderSingleFile(tempFile);

            Iterator<IndexJournalEntryTermData> iter;
            IndexJournalEntryTermData termData;

            try (var ptr = reader.newPointer()) {

                /** DOCUMENT 1 */
                assertTrue(ptr.nextDocument());
                assertEquals(11, ptr.documentId());
                assertEquals(22, ptr.documentFeatures());
                assertEquals(33, ptr.documentMeta());
                assertEquals(10, ptr.documentSize());

                iter = ptr.iterator();

                // Term 1
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word1"), termData.termId());
                assertEquals(44, termData.metadata());
                assertEquals(IntList.of(1, 3, 5), termData.positions().values());

                // Term 2
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word2"), termData.termId());
                assertEquals(55, termData.metadata());
                assertEquals(IntList.of(2, 4, 6), termData.positions().values());

                // No more terms

                assertFalse(iter.hasNext());

                /** DOCUMENT 2 */
                assertTrue(ptr.nextDocument());
                assertEquals(12, ptr.documentId());
                assertEquals(23, ptr.documentFeatures());
                assertEquals(34, ptr.documentMeta());
                assertEquals(11, ptr.documentSize());

                iter = ptr.iterator();
                // Term 1
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word1"), termData.termId());
                assertEquals(45, termData.metadata());
                assertEquals(IntList.of(2, 4, 6), termData.positions().values());

                // Term 2
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word2"), termData.termId());
                assertEquals(56, termData.metadata());
                assertEquals(IntList.of(3, 5, 7), termData.positions().values());

                // No more terms
                assertFalse(iter.hasNext());

                // No more documents
                assertFalse(ptr.nextDocument());
            }
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }
    }

    @Test
    public void testMultiFile() {
        try (var writer = new IndexJournalWriterSingleFileImpl(tempFile)) {
            writer.put(new IndexJournalEntryHeader(11, 22, 10, 33),
                    new IndexJournalEntryData(
                        new String[]{"word1", "word2"},
                        new long[]{44, 55},
                        new GammaCodedSequence[]{
                                gcs(1, 3, 5),
                                gcs(2, 4, 6),
                        })
                    );
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }

        try (var writer = new IndexJournalWriterSingleFileImpl(tempFile2)) {
            writer.put(new IndexJournalEntryHeader(12, 23, 11, 34),
                    new IndexJournalEntryData(
                        new String[]{"word1", "word2"},
                        new long[]{45, 56},
                        new GammaCodedSequence[]{
                                gcs(2, 4, 6),
                                gcs(3, 5, 7),
                        })
                    );
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }

        // Read the journal back

        try {
            var reader = new IndexJournalReaderPagingImpl(List.of(tempFile, tempFile2));

            Iterator<IndexJournalEntryTermData> iter;
            IndexJournalEntryTermData termData;

            try (var ptr = reader.newPointer()) {

                /** DOCUMENT 1 */
                assertTrue(ptr.nextDocument());
                assertEquals(11, ptr.documentId());
                assertEquals(22, ptr.documentFeatures());
                assertEquals(33, ptr.documentMeta());
                assertEquals(10, ptr.documentSize());

                iter = ptr.iterator();

                // Term 1
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word1"), termData.termId());
                assertEquals(44, termData.metadata());
                assertEquals(IntList.of(1, 3, 5), termData.positions().values());

                // Term 2
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word2"), termData.termId());
                assertEquals(55, termData.metadata());
                assertEquals(IntList.of(2, 4, 6), termData.positions().values());

                // No more terms

                assertFalse(iter.hasNext());

                /** DOCUMENT 2 */
                assertTrue(ptr.nextDocument());
                assertEquals(12, ptr.documentId());
                assertEquals(23, ptr.documentFeatures());
                assertEquals(34, ptr.documentMeta());
                assertEquals(11, ptr.documentSize());

                iter = ptr.iterator();
                // Term 1
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word1"), termData.termId());
                assertEquals(45, termData.metadata());
                assertEquals(IntList.of(2, 4, 6), termData.positions().values());

                // Term 2
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word2"), termData.termId());
                assertEquals(56, termData.metadata());
                assertEquals(IntList.of(3, 5, 7), termData.positions().values());

                // No more terms
                assertFalse(iter.hasNext());

                // No more documents
                assertFalse(ptr.nextDocument());
            }
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }
    }

    @Test
    public void testSingleFileIterTwice() {
        try (var writer = new IndexJournalWriterSingleFileImpl(tempFile)) {
            // Write two documents with two terms each
            writer.put(new IndexJournalEntryHeader(11, 22, 10, 33),
                    new IndexJournalEntryData(
                        new String[]{"word1", "word2"},
                        new long[]{44, 55},
                        new GammaCodedSequence[]{
                                gcs(1, 3, 5),
                                gcs(2, 4, 6),
                        })
                    );
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }

        // Read the journal back

        try {
            var reader = new IndexJournalReaderSingleFile(tempFile);

            Iterator<IndexJournalEntryTermData> iter;
            IndexJournalEntryTermData termData;

            try (var ptr = reader.newPointer()) {

                /** DOCUMENT 1 */
                assertTrue(ptr.nextDocument());
                assertEquals(11, ptr.documentId());
                assertEquals(22, ptr.documentFeatures());
                assertEquals(10, ptr.documentSize());
                assertEquals(33, ptr.documentMeta());

                iter = ptr.iterator();
                // Term 1
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word1"), termData.termId());
                assertEquals(44, termData.metadata());
                assertEquals(IntList.of(1, 3, 5), termData.positions().values());

                // Ensure we can iterate again over the same document without persisting state or closing the pointer

                iter = ptr.iterator();
                // Term 1
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word1"), termData.termId());
                assertEquals(44, termData.metadata());
                assertEquals(IntList.of(1, 3, 5), termData.positions().values());
            }
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }
    }

    @Test
    public void testFiltered() {
        try (var writer = new IndexJournalWriterSingleFileImpl(tempFile)) {
            // Write two documents with two terms each
            writer.put(new IndexJournalEntryHeader(11, 22, 10, 33),
                    new IndexJournalEntryData(
                        new String[]{"word1", "word2"},
                        new long[]{44, 55},
                        new GammaCodedSequence[]{
                                gcs(1, 3, 5),
                                gcs(2, 4, 6),
                        })
                    );
            writer.put(new IndexJournalEntryHeader(12, 23, 11, 34),
                    new IndexJournalEntryData(
                            new String[]{"word1", "word2"},
                        new long[]{45, 56},
                        new GammaCodedSequence[]{
                                gcs(2, 4, 6),
                                gcs(3, 5, 7),
                        }
                    ));
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }

        // Read the journal back

        try {
            var reader = new IndexJournalReaderSingleFile(tempFile).filtering(meta -> meta == 45);

            Iterator<IndexJournalEntryTermData> iter;
            IndexJournalEntryTermData termData;

            try (var ptr = reader.newPointer()) {
                /** DOCUMENT 2 */
                assertTrue(ptr.nextDocument());
                assertEquals(12, ptr.documentId());
                assertEquals(23, ptr.documentFeatures());
                assertEquals(34, ptr.documentMeta());
                assertEquals(11, ptr.documentSize());

                iter = ptr.iterator();
                // Term 1
                assertTrue(iter.hasNext());
                termData = iter.next();
                assertEquals(wordId("word1"), termData.termId());
                assertEquals(45, termData.metadata());
                assertEquals(IntList.of(2, 4, 6), termData.positions().values());

                // No more terms
                assertFalse(iter.hasNext());
                // No more documents
                assertFalse(ptr.nextDocument());
            }
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }
    }

    @Test
    public void testIntegrationScenario() throws IOException {
        Map<Long, Integer> wordMap = new HashMap<>();
        for (int i = 0; i < 512; i++) {
            wordMap.put(hasher.hashKeyword(Integer.toString(i)), i);
        }
        try (var writer = new IndexJournalWriterSingleFileImpl(tempFile)) {
            for (int idc = 1; idc < 512; idc++) {
                int id = idc;
                int[] factors = IntStream
                        .rangeClosed(1, id)
                        .filter(v -> (id % v) == 0)
                        .toArray();

                System.out.println("id:" + id + " factors: " + Arrays.toString(factors));

                long fullId = UrlIdCodec.encodeId((32 - (id % 32)), id);

                var header = new IndexJournalEntryHeader(factors.length, 0, 100, fullId, new DocumentMetadata(0, 0, 0, 0, id % 5, id, id % 20, (byte) 0).encode());

                String[] keywords = IntStream.of(factors).mapToObj(Integer::toString).toArray(String[]::new);
                long[] metadata = new long[factors.length];
                for (int i = 0; i < factors.length; i++) {
                    metadata[i] = new WordMetadata(i, EnumSet.of(WordFlags.Title)).encode();
                }
                GammaCodedSequence[] positions = new GammaCodedSequence[factors.length];
                ByteBuffer wa = ByteBuffer.allocate(16);
                for (int i = 0; i < factors.length; i++) {
                    positions[i] = GammaCodedSequence.generate(wa, i + 1);
                }

                writer.put(header, new IndexJournalEntryData(keywords, metadata, positions));
            }
        }

        try (var ptr = new IndexJournalReaderSingleFile(tempFile).newPointer()) {
            while (ptr.nextDocument()) {
                int ordinal = UrlIdCodec.getDocumentOrdinal(ptr.documentId());
                System.out.println(ordinal);

                var expectedFactors =
                        new LongArrayList(IntStream
                        .rangeClosed(1, ordinal)
                        .filter(v -> (ordinal % v) == 0)
                        .mapToObj(Integer::toString)
                        .mapToLong(hasher::hashKeyword)
                        .toArray());

                LongList foundIds = new LongArrayList();

                var iter = ptr.iterator();
                while (iter.hasNext()) {
                    var termData = iter.next();
                    foundIds.add(termData.termId());
                }

                if (!expectedFactors.equals(foundIds)) {
                    System.out.println("Found: ");
                    System.out.println(foundIds.stream().map(fac -> wordMap.getOrDefault(fac, -1)).map(Objects::toString).collect(Collectors.joining(",")));
                    System.out.println("Expected: ");
                    System.out.println(expectedFactors.stream().map(fac -> wordMap.getOrDefault(fac, -1)).map(Objects::toString).collect(Collectors.joining(",")));
                    fail();
                }
                assertEquals(expectedFactors, foundIds);
            }
        }
    }

}
