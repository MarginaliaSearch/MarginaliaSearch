package nu.marginalia.wmsa.edge.index.service;

import lombok.SneakyThrows;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexPartitioner;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalReader;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class SearchIndexJournalWriterTest {
    KeywordLexicon keywordLexicon;
    SearchIndexJournalWriterImpl writer;

    Path indexFile;
    Path wordsFile1;
    Path urlsFile1;
    Path dictionaryFile;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @BeforeEach @SneakyThrows
    void setUp() {
        dictionaryFile = Files.createTempFile("tmp", ".dict");
        dictionaryFile.toFile().deleteOnExit();

        keywordLexicon = new KeywordLexicon(new KeywordLexiconJournal(dictionaryFile.toFile()), new DictionaryHashMap(1L<<16));

        indexFile = Files.createTempFile("tmp", ".idx");
        indexFile.toFile().deleteOnExit();
        writer = new SearchIndexJournalWriterImpl(keywordLexicon, indexFile.toFile());

        wordsFile1 = Files.createTempFile("words1", ".idx");
        urlsFile1 = Files.createTempFile("urls1", ".idx");
    }

    @SneakyThrows
    @AfterEach
    void tearDown() {
        keywordLexicon.close();
        writer.close();
        indexFile.toFile().delete();
        dictionaryFile.toFile().delete();
        urlsFile1.toFile().delete();
        wordsFile1.toFile().delete();
    }

    @Test
    void put() throws IOException, InterruptedException {

        for (int i = 0; i < 512; i++) {
            if (i % 2 == 0) {
                writer.put(new SearchIndexJournalEntryHeader(4, i, IndexBlock.Words_1),
                        new SearchIndexJournalEntry(new long[]{keywordLexicon.getOrInsert("one"),
                                0x000000,
                                keywordLexicon.getOrInsert("two"),
                                0xFFFFFF}));
            }
            else {
                writer.put(new SearchIndexJournalEntryHeader(2, i, IndexBlock.Words_1),
                        new SearchIndexJournalEntry(new long[]{keywordLexicon.getOrInsert("one"),
                                0x000000}));
            }
        }
        keywordLexicon.commitToDisk();
        Thread.sleep(1000);
        writer.forceWrite();

        var reader = new SearchIndexJournalReader(MultimapFileLong.forReading(indexFile));

        for (var entry : reader) {
            logger.info("{}, {} {}", entry, entry.urlId(), entry.domainId());
            for (var record : entry.readEntry()) {
                logger.info("{}", record);
            }
        }

        new SearchIndexConverter(IndexBlock.Words_1, 7, Path.of("/tmp"),
                indexFile.toFile(),
                wordsFile1.toFile(),
                urlsFile1.toFile(),
                new SearchIndexPartitioner(null), (url) -> false)
                .convert();

        MultimapFileLong mmf = MultimapFileLong.forReading(urlsFile1);
        for (int i = 0; i < 1056; i++) {
            System.out.println(i + ":" + mmf.get(i));
        }
        try (var idx = new SearchIndex("test", urlsFile1.toFile(), wordsFile1.toFile())) {
            for (String s : List.of("one", "two", "3")) {
                System.out.println("***" + s);
                var range = idx.rangeForWord(keywordLexicon.getOrInsert(s));
                System.out.println(range);

                System.out.println(1 + "? " + range.hasUrl(1));
                System.out.println(2 + "? " + range.hasUrl(2));

                var source = range.asEntrySource();
                System.out.println(source);

            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    void testWeirdScenario() throws IOException, InterruptedException {
        long[] vals = new long[]{3818531806586L, 1696527885824L, 3818531806586L, 1679348016640L, 3818531806611L, 1168242909952L, 3818531806611L, 1168242909952L, 4316748027839L, 549761847552L, 47240643248522L, 285873040601600L, 51101820141195L, 1099517497600L, 51101820141295L, 549762863360L};

        for (int v = 0; v < vals.length / 2; v++) {
            writer.put(new SearchIndexJournalEntryHeader(4, vals[v * 2], IndexBlock.Words_1),
                    new SearchIndexJournalEntry(new long[]{keywordLexicon.getOrInsert("one"), vals[v * 2 + 1]}));
        }

        keywordLexicon.commitToDisk();
        Thread.sleep(1000);
        writer.forceWrite();

        var reader = new SearchIndexJournalReader(MultimapFileLong.forReading(indexFile));

        for (var entry : reader) {
            logger.info("{}, {} {}", entry, entry.urlId(), entry.domainId());
            for (var record : entry.readEntry()) {
                logger.info("{}", record);
            }
        }

        new SearchIndexConverter(IndexBlock.Words_1, 7, Path.of("/tmp"),
                indexFile.toFile(),
                wordsFile1.toFile(),
                urlsFile1.toFile(),
                new SearchIndexPartitioner(null), (url) -> false)
                .convert();

        try (var idx = new SearchIndex("test", urlsFile1.toFile(), wordsFile1.toFile())) {
            var range = idx.rangeForWord(keywordLexicon.getOrInsert("one"));
            long[] buffer = new long[128];

        }
        catch (Exception ex) { ex.printStackTrace(); }

    }

}