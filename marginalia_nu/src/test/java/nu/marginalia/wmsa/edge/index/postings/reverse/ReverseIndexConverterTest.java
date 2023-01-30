package nu.marginalia.wmsa.edge.index.postings.reverse;

import lombok.SneakyThrows;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.dict.OffHeapDictionaryHashMap;
import nu.marginalia.util.test.TestUtil;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalReaderSingleFile;
import nu.marginalia.wmsa.edge.index.postings.journal.writer.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.postings.reverse.query.ReverseIndexEntrySourceBehavior;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ReverseIndexConverterTest {
    KeywordLexicon keywordLexicon;
    SearchIndexJournalWriterImpl writer;

    Path indexFile;
    Path wordsFile1;
    Path urlsFile1;
    Path dictionaryFile;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @BeforeEach
    @SneakyThrows
    void setUp() {
        dictionaryFile = Files.createTempFile("tmp", ".dict");
        dictionaryFile.toFile().deleteOnExit();

        keywordLexicon = new KeywordLexicon(new KeywordLexiconJournal(dictionaryFile.toFile()), new OffHeapDictionaryHashMap(1L<<16));
        keywordLexicon.getOrInsert("0");

        indexFile = Files.createTempFile("tmp", ".idx");
        indexFile.toFile().deleteOnExit();
        writer = new SearchIndexJournalWriterImpl(keywordLexicon, indexFile.toFile());

        wordsFile1 = Files.createTempFile("words1", ".idx");
        urlsFile1 = Files.createTempFile("urls1", ".idx");
    }

    public void createEntry(SearchIndexJournalWriterImpl writer, KeywordLexicon keywordLexicon, int id) {
        int[] factors = IntStream.rangeClosed(1, id).filter(v -> (id % v) == 0).toArray();
        var header = new SearchIndexJournalEntryHeader(factors.length, id, EdgePageDocumentsMetadata.defaultValue());

        long[] data = new long[factors.length*2];
        for (int i = 0; i < factors.length; i++) {
            data[2*i] = keywordLexicon.getOrInsert(Integer.toString(factors[i]));
            data[2*i + 1] = factors[i];
        }

        writer.put(header, new SearchIndexJournalEntry(data));
    }

    @Test
    void testReverseIndex() throws IOException, InterruptedException {
        for (int i = 1; i < 512; i++) {
            createEntry(writer, keywordLexicon, i);
        }


        keywordLexicon.commitToDisk();
        Thread.sleep(1000);
        writer.forceWrite();


        Path tmpDir = Path.of("/tmp");
        Path dataDir = Files.createTempDirectory(getClass().getSimpleName());

        var wordsFile = dataDir.resolve("urls.dat");
        var docsFile = dataDir.resolve("docs.dat");
        var journalReader = new SearchIndexJournalReaderSingleFile(LongArray.mmapRead(indexFile));

        new ReverseIndexConverter(tmpDir, journalReader, wordsFile, docsFile)
                .convert();

        var reverseIndexReader = new ReverseIndexReader(wordsFile, docsFile);

        System.out.println(reverseIndexReader.numDocuments(keywordLexicon.getReadOnly("1")));
        System.out.println(reverseIndexReader.numDocuments(keywordLexicon.getReadOnly("2")));
        System.out.println(reverseIndexReader.numDocuments(keywordLexicon.getReadOnly("3")));

        System.out.println(reverseIndexReader.isWordInDoc(keywordLexicon.getReadOnly("1"), 1));
        System.out.println(reverseIndexReader.isWordInDoc(keywordLexicon.getReadOnly("2"), 1));
        System.out.println(reverseIndexReader.isWordInDoc(keywordLexicon.getReadOnly("1"), 2));
        System.out.println(reverseIndexReader.isWordInDoc(keywordLexicon.getReadOnly("2"), 2));
        System.out.println(reverseIndexReader.isWordInDoc(keywordLexicon.getReadOnly("1"), 3));
        System.out.println(reverseIndexReader.isWordInDoc(keywordLexicon.getReadOnly("2"), 3));

        var buffer = new LongQueryBuffer(32);
        reverseIndexReader.documents(keywordLexicon.getReadOnly("1"), ReverseIndexEntrySourceBehavior.DO_PREFER).read(buffer);
        assertArrayEquals(LongStream.range(1, 17).toArray(), buffer.copyData());
        System.out.println(buffer);

        buffer.reset();
        reverseIndexReader.documents(keywordLexicon.getReadOnly("2"), ReverseIndexEntrySourceBehavior.DO_PREFER).read(buffer);
        assertArrayEquals(LongStream.range(1, 17).map(v -> v*2).toArray(), buffer.copyData());
        System.out.println(buffer);

        buffer.reset();
        reverseIndexReader.documents(keywordLexicon.getReadOnly("3"), ReverseIndexEntrySourceBehavior.DO_PREFER).read(buffer);
        assertArrayEquals(LongStream.range(1, 17).map(v -> v*3).toArray(), buffer.copyData());
        System.out.println(buffer);

        buffer.reset();
        var es = reverseIndexReader.documents(keywordLexicon.getReadOnly("7"), ReverseIndexEntrySourceBehavior.DO_PREFER);
        do {
            buffer.reset();
            es.read(buffer);
            System.out.println(buffer);
        } while (es.hasMore());


        TestUtil.clearTempDir(dataDir);
    }
}