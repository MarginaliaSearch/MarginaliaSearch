package nu.marginalia.wmsa.edge.index.postings.reverse;

import lombok.SneakyThrows;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.dict.OffHeapDictionaryHashMap;
import nu.marginalia.util.test.TestUtil;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalReaderSingleFile;
import nu.marginalia.wmsa.edge.index.postings.journal.writer.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.postings.reverse.query.ReverseIndexEntrySourceBehavior;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

class ReverseIndexConverterTest2 {

    KeywordLexicon keywordLexicon;
    SearchIndexJournalWriterImpl writer;

    Path indexFile;
    Path wordsFile1;
    Path urlsFile1;
    Path dictionaryFile;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    Path dataDir;
    private Path wordsFile;
    private Path docsFile;

    int workSetSize = 8192;
    int workSetStart = 8000;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        dictionaryFile = Files.createTempFile("tmp", ".dict");
        dictionaryFile.toFile().deleteOnExit();

        keywordLexicon = new KeywordLexicon(new KeywordLexiconJournal(dictionaryFile.toFile()), new OffHeapDictionaryHashMap(1L<<18));
        keywordLexicon.getOrInsert("0");

        indexFile = Files.createTempFile("tmp", ".idx");
        indexFile.toFile().deleteOnExit();
        writer = new SearchIndexJournalWriterImpl(keywordLexicon, indexFile.toFile());

        wordsFile1 = Files.createTempFile("words1", ".idx");
        urlsFile1 = Files.createTempFile("urls1", ".idx");

        dataDir = Files.createTempDirectory(getClass().getSimpleName());

        for (int i = 1; i < workSetSize; i++) {
            if (i < workSetStart) {
                keywordLexicon.getOrInsert(Integer.toString(i));
            }
            else {
                createEntry(writer, keywordLexicon, i);
            }
        }

        keywordLexicon.commitToDisk();
        Thread.sleep(1000);
        writer.forceWrite();

        var reader = new SearchIndexJournalReaderSingleFile(LongArray.mmapRead(indexFile));

        wordsFile = dataDir.resolve("words.dat");
        docsFile = dataDir.resolve("docs.dat");
    }

    @AfterEach
    public void tearDown() {
        TestUtil.clearTempDir(dataDir);
    }

    public int[] getFactorsI(int id) {
        return IntStream.rangeClosed(1, id-1).toArray();
    }
    public long[] getFactorsL(int id) {
        return LongStream.rangeClosed(1, id).filter(v -> (id % v) == 0).toArray();
    }

    long createId(long url, long domain) {
        return (domain << 32) | url;
    }
    public void createEntry(SearchIndexJournalWriterImpl writer, KeywordLexicon keywordLexicon, int id) {
        int[] factors = getFactorsI(id);
        var header = new SearchIndexJournalEntryHeader(factors.length, createId(id, id/20), id % 5);

        long[] data = new long[factors.length*2];
        for (int i = 0; i < factors.length; i++) {
            data[2*i] = keywordLexicon.getOrInsert(Integer.toString(factors[i]));
            data[2*i + 1] = (i % 21 != 0) ? 0 : -factors[i];
        }

        writer.put(header, new SearchIndexJournalEntry(data));
    }

    @Test
    void testRev2() throws IOException {

        Path tmpDir = Path.of("/tmp");

        new ReverseIndexConverter(tmpDir, new SearchIndexJournalReaderSingleFile(LongArray.mmapRead(indexFile)), wordsFile, docsFile).convert();

        var reverseReader = new ReverseIndexReader(wordsFile, docsFile);

        for (int i = workSetStart; i < workSetSize; i++) {

            var es = reverseReader.documents(i, ReverseIndexEntrySourceBehavior.DO_PREFER);
            LongQueryBuffer lqb = new LongQueryBuffer(100);
            while (es.hasMore()) {
                lqb.reset();
                es.read(lqb);
                System.out.println(Arrays.toString(Arrays.copyOf(lqb.data, lqb.end)));
            }
            System.out.println("--");
        }

        TestUtil.clearTempDir(dataDir);
    }


    @Test
    void testRevP() throws IOException {

        Path tmpDir = Path.of("/tmp");

        new ReverseIndexConverter(tmpDir, new SearchIndexJournalReaderSingleFile(LongArray.mmapRead(indexFile), null, ReverseIndexPriorityParameters::filterPriorityRecord), wordsFile, docsFile).convert();

        var reverseReader = new ReverseIndexReader(wordsFile, docsFile);

        for (int i = workSetStart; i < workSetSize; i++) {

            var es = reverseReader.documents(i, ReverseIndexEntrySourceBehavior.DO_PREFER);
            LongQueryBuffer lqb = new LongQueryBuffer(100);
            while (es.hasMore()) {
                lqb.reset();
                es.read(lqb);
                System.out.println(Arrays.toString(Arrays.copyOf(lqb.data, lqb.end)));
            }
            System.out.println("--");
        }

        TestUtil.clearTempDir(dataDir);
    }

}