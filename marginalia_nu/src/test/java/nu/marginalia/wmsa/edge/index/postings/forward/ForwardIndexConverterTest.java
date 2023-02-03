package nu.marginalia.wmsa.edge.index.postings.forward;

import lombok.SneakyThrows;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.dict.OffHeapDictionaryHashMap;
import nu.marginalia.util.test.TestUtil;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.wmsa.edge.index.postings.DomainRankings;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.postings.journal.reader.SearchIndexJournalReaderSingleFile;
import nu.marginalia.wmsa.edge.index.postings.journal.writer.SearchIndexJournalWriterImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForwardIndexConverterTest {

    KeywordLexicon keywordLexicon;
    SearchIndexJournalWriterImpl writer;

    Path indexFile;
    Path wordsFile1;
    Path urlsFile1;
    Path dictionaryFile;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    Path dataDir;
    private Path docsFileId;
    private Path docsFileData;

    int workSetSize = 512;
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
            createEntry(writer, keywordLexicon, i);
        }


        keywordLexicon.commitToDisk();
        Thread.sleep(1000);
        writer.forceWrite();


        var reader = new SearchIndexJournalReaderSingleFile(LongArray.mmapRead(indexFile));

        docsFileId = dataDir.resolve("docs-i.dat");
        docsFileData = dataDir.resolve("docs-d.dat");
    }

    @AfterEach
    public void tearDown() {
        TestUtil.clearTempDir(dataDir);
    }

    public int[] getFactorsI(int id) {
        return IntStream.rangeClosed(1, id).filter(v -> (id % v) == 0).toArray();
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
            data[2*i + 1] = -factors[i];
        }

        writer.put(header, new SearchIndexJournalEntry(data));
    }

    @Test
    void testForwardIndex() throws IOException {

        new ForwardIndexConverter(indexFile.toFile(), docsFileId, docsFileData, new DomainRankings()).convert();

        var forwardReader = new ForwardIndexReader(docsFileId, docsFileData);

        for (int i = 36; i < workSetSize; i++) {
            assertEquals(0x00FF000000000000L | (i % 5), forwardReader.getDocMeta(i));
            assertEquals(i/20, forwardReader.getDomainId(i));
        }

    }


}