package nu.marginalia.index.reverse;

import lombok.SneakyThrows;
import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.index.full.ReverseIndexFullConverter;
import nu.marginalia.index.full.ReverseIndexFullReader;
import nu.marginalia.index.journal.model.IndexJournalEntry;
import nu.marginalia.index.journal.reader.IndexJournalReaderSingleCompressedFile;
import nu.marginalia.index.journal.writer.IndexJournalWriterImpl;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.lexicon.journal.KeywordLexiconJournalMode;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.process.control.ProcessTaskHeartbeat;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.lexicon.KeywordLexicon;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.test.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.when;

class ReverseIndexFullConverterTest {
    KeywordLexicon keywordLexicon;

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

        keywordLexicon = new KeywordLexicon(new KeywordLexiconJournal(dictionaryFile.toFile(), KeywordLexiconJournalMode.READ_WRITE));
        keywordLexicon.getOrInsert("0");

        indexFile = Files.createTempFile("tmp", ".idx");
        indexFile.toFile().deleteOnExit();


        wordsFile1 = Files.createTempFile("words1", ".idx");
        urlsFile1 = Files.createTempFile("urls1", ".idx");
    }

    public void createEntry(IndexJournalWriter writer, KeywordLexicon keywordLexicon, int id) {
        int[] factors = IntStream.rangeClosed(1, id).filter(v -> (id % v) == 0).toArray();

        var entryBuilder = IndexJournalEntry.builder(id, DocumentMetadata.defaultValue());

        for (int i = 0; i < factors.length; i++) {
            entryBuilder.add(keywordLexicon.getOrInsert(Integer.toString(factors[i])), -factors[i]);
        }

        writer.put(entryBuilder.build());
    }

    @Test
    void testReverseIndex() throws IOException {
        var writer = new IndexJournalWriterImpl(keywordLexicon, indexFile);

        for (int i = 1; i < 512; i++) {
            createEntry(writer, keywordLexicon, i);
        }

        writer.close();


        Path tmpDir = Path.of("/tmp");
        Path dataDir = Files.createTempDirectory(getClass().getSimpleName());

        var wordsFile = dataDir.resolve("urls.dat");
        var docsFile = dataDir.resolve("docs.dat");
        var journalReader = new IndexJournalReaderSingleCompressedFile(indexFile);

        // RIP fairies
        var processHeartbeat = Mockito.mock(ProcessHeartbeat.class);
        when(processHeartbeat.createProcessTaskHeartbeat(Mockito.any(), Mockito.any()))
                .thenReturn(Mockito.mock(ProcessTaskHeartbeat.class));

        new ReverseIndexFullConverter(
                processHeartbeat,
                tmpDir, journalReader, new DomainRankings(), wordsFile, docsFile)
                .convert();

        var reverseIndexReader = new ReverseIndexFullReader(wordsFile, docsFile);

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
        reverseIndexReader.documents(keywordLexicon.getReadOnly("1")).read(buffer);
        assertArrayEquals(LongStream.range(1, 17).map(this::addMaxRank).toArray(), buffer.copyData());
        System.out.println(buffer);

        buffer.reset();
        reverseIndexReader.documents(keywordLexicon.getReadOnly("2")).read(buffer);
        assertArrayEquals(LongStream.range(1, 17).map(v -> v*2).map(this::addMaxRank).toArray(), buffer.copyData());
        System.out.println(buffer);

        buffer.reset();
        reverseIndexReader.documents(keywordLexicon.getReadOnly("3")).read(buffer);
        assertArrayEquals(LongStream.range(1, 17).map(v -> v*3).map(this::addMaxRank).toArray(), buffer.copyData());
        System.out.println(buffer);

        buffer.reset();
        var es = reverseIndexReader.documents(keywordLexicon.getReadOnly("7"));
        do {
            buffer.reset();
            es.read(buffer);
            System.out.println(buffer);
        } while (es.hasMore());


        TestUtil.clearTempDir(dataDir);
    }

    // Add a max domain rank component to the input, when interpreted as an ID
    private long addMaxRank(long in) {
        return UrlIdCodec.addRank(1f, in);
    }
}