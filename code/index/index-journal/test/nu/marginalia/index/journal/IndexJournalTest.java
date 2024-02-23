package nu.marginalia.index.journal;

import nu.marginalia.index.journal.model.IndexJournalEntry;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.index.journal.reader.IndexJournalReaderSingleFile;
import nu.marginalia.index.journal.writer.IndexJournalWriterSingleFileImpl;
import nu.marginalia.model.id.UrlIdCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndexJournalTest {
    Path tempFile;
    IndexJournalReader reader;

    long firstDocId = UrlIdCodec.encodeId(44, 10);
    long secondDocId = UrlIdCodec.encodeId(43, 15);

    @BeforeEach
    public void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".dat");

        var journalWriter = new IndexJournalWriterSingleFileImpl( tempFile);
        journalWriter.put(IndexJournalEntry.builder(44, 10, 55)
                .add(1, 2)
                .add(2, 3)
                .add(3, 4)
                .add(5, 6).build());

        journalWriter.put(IndexJournalEntry.builder(43, 15, 10)
                .add(5, 5)
                .add(6, 6)
                .build());
        journalWriter.close();

        reader = new IndexJournalReaderSingleFile(tempFile);
    }
    @AfterEach
    public void tearDown() throws IOException {
        Files.delete(tempFile);
    }

    @Test
    public void forEachDocId() {
        List<Long> expected = List.of(firstDocId, secondDocId);
        List<Long> actual = new ArrayList<>();

        reader.forEachDocId(actual::add);
        assertEquals(expected, actual);
    }

    @Test
    public void forEachWordId() {
        List<Integer> expected = List.of(1, 2, 3, 5, 5 ,6);
        List<Integer> actual = new ArrayList<>();

        reader.forEachWordId(i -> actual.add((int) i));
        assertEquals(expected, actual);
    }

}
