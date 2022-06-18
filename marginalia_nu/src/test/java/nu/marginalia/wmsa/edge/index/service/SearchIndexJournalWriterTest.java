package nu.marginalia.wmsa.edge.index.service;

import lombok.SneakyThrows;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalReader;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.dictionary.DictionaryWriter;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexReader;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.reader.query.IndexSearchBudget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

class SearchIndexJournalWriterTest {
    DictionaryWriter dictionaryWriter;
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

        dictionaryWriter = new DictionaryWriter(dictionaryFile.toFile(), 1L<<16, false);

        indexFile = Files.createTempFile("tmp", ".idx");
        indexFile.toFile().deleteOnExit();
        writer = new SearchIndexJournalWriterImpl(dictionaryWriter, indexFile.toFile());

        wordsFile1 = Files.createTempFile("words1", ".idx");
        urlsFile1 = Files.createTempFile("urls1", ".idx");
    }

    @SneakyThrows
    @AfterEach
    void tearDown() {
        dictionaryWriter.close();
        writer.close();
        indexFile.toFile().delete();
        dictionaryFile.toFile().delete();
        urlsFile1.toFile().delete();
        wordsFile1.toFile().delete();
    }

    @Test
    void put() throws IOException {
        writer.put(new SearchIndexJournalEntryHeader(4, (1234L << 32) | 5678, IndexBlock.Link),
                new SearchIndexJournalEntry(new long[] { 1, 2, 3, 4 }));
        writer.put(new SearchIndexJournalEntryHeader(4, (2345L << 32) | 2244, IndexBlock.Words),
                new SearchIndexJournalEntry(new long[] { 5, 6, 7 }));
        writer.forceWrite();

        var reader = new SearchIndexJournalReader(MultimapFileLong.forReading(indexFile));
        reader.forEach(entry -> {
            logger.info("{}, {} {}", entry, entry.urlId(), entry.domainId());
            logger.info("{}", entry.readEntry().toArray());
        });
    }

}