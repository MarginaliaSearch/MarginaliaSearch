package nu.marginalia.index.journal;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.model.IndexJournalEntryTermData;
import nu.marginalia.index.journal.reader.IndexJournalReaderPagingImpl;
import nu.marginalia.index.journal.writer.IndexJournalWriterSingleFileImpl;
import nu.marginalia.index.journal.reader.IndexJournalReaderSingleFile;
import nu.marginalia.sequence.GammaCodedSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

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
            writer.put(new IndexJournalEntryHeader(11, 22, 33),
                    new IndexJournalEntryData(
                        new String[]{"word1", "word2"},
                        new long[]{44, 55},
                        new GammaCodedSequence[]{
                                gcs(1, 3, 5),
                                gcs(2, 4, 6),
                        })
                    );
            writer.put(new IndexJournalEntryHeader(12, 23, 34),
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
            writer.put(new IndexJournalEntryHeader(11, 22, 33),
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
            writer.put(new IndexJournalEntryHeader(12, 23, 34),
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
            writer.put(new IndexJournalEntryHeader(11, 22, 33),
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
            writer.put(new IndexJournalEntryHeader(11, 22, 33),
                    new IndexJournalEntryData(
                        new String[]{"word1", "word2"},
                        new long[]{44, 55},
                        new GammaCodedSequence[]{
                                gcs(1, 3, 5),
                                gcs(2, 4, 6),
                        })
                    );
            writer.put(new IndexJournalEntryHeader(12, 23, 34),
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

}
