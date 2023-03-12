package nu.marginalia.index.journal;

import nu.marginalia.index.journal.model.IndexJournalEntry;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.index.journal.reader.IndexJournalReaderSingleCompressedFile;
import nu.marginalia.index.journal.writer.IndexJournalWriterImpl;
import nu.marginalia.lexicon.KeywordLexicon;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndexJournalTest {
    Path tempFile;
    KeywordLexicon lexicon;
    IndexJournalReader reader;

    @BeforeEach
    public void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".dat");
        lexicon = Mockito.mock(KeywordLexicon.class);

        var journalWriter = new IndexJournalWriterImpl(lexicon, tempFile);
        journalWriter.put(IndexJournalEntry.builder(44, 10, 55)
                .add(1, 2)
                .add(2, 3)
                .add(3, 4)
                .add(5, 6).build());

        journalWriter.put(IndexJournalEntry.builder(43, 15, 10)
                .add(5, 5)
                .add(6, 6)
                .build());
        journalWriter.forceWrite();
        journalWriter.close();

        reader = new IndexJournalReaderSingleCompressedFile(tempFile);
    }
    @AfterEach
    public void tearDown() throws IOException {
        reader.close();
        Files.delete(tempFile);
    }

    @Test
    public void reiterable() {
        // Verifies that the reader can be run twice to the same effect

        int cnt = 0;
        int cnt2 = 0;

        for (var item : reader) cnt++;
        for (var item : reader) cnt2++;

        assertEquals(cnt2, cnt);
    }

    @Test
    public void forEachUrlId() {
        List<Integer> expected = List.of(10, 15);
        List<Integer> actual = new ArrayList<>();

        reader.forEachUrlId(actual::add);
        assertEquals(expected, actual);
    }

    @Test
    public void forEachWordId() {
        List<Integer> expected = List.of(1, 2, 3, 5, 5 ,6);
        List<Integer> actual = new ArrayList<>();

        reader.forEachWordId(actual::add);
        assertEquals(expected, actual);
    }


    @Test
    public void forEachUrlIdWordId() {
        List<Pair<Integer, Integer>> expected = List.of(
                Pair.of(10, 1),
                Pair.of(10, 2),
                Pair.of(10, 3),
                Pair.of(10, 5),
                Pair.of(15, 5),
                Pair.of(15, 6));
        List<Pair<Integer, Integer>> actual = new ArrayList<>();

        reader.forEachUrlIdWordId((url, word) -> actual.add(Pair.of(url, word)));
        assertEquals(expected, actual);
    }

    @Test
    public void forEachDocIdWordId() {
        List<Pair<Long, Integer>> expected = List.of(
                Pair.of(10L | (44L << 32), 1),
                Pair.of(10L | (44L << 32), 2),
                Pair.of(10L | (44L << 32), 3),
                Pair.of(10L | (44L << 32), 5),
                Pair.of(15L | (43L << 32), 5),
                Pair.of(15L | (43L << 32), 6));
        List<Pair<Long, Integer>> actual = new ArrayList<>();

        reader.forEachDocIdWordId((url, word) -> actual.add(Pair.of(url, word)));
        assertEquals(expected, actual);
    }

    @Test
    public void forEachDocIdRecord() {
        List<Pair<Long, IndexJournalEntryData.Record>> expected = List.of(
                Pair.of(10L | (44L << 32), new IndexJournalEntryData.Record(1, 2)),
                Pair.of(10L | (44L << 32), new IndexJournalEntryData.Record(2, 3)),
                Pair.of(10L | (44L << 32), new IndexJournalEntryData.Record(3, 4)),
                Pair.of(10L | (44L << 32), new IndexJournalEntryData.Record(5, 6)),
                Pair.of(15L | (43L << 32), new IndexJournalEntryData.Record(5, 5)),
                Pair.of(15L | (43L << 32), new IndexJournalEntryData.Record(6, 6))
        );
        List<Pair<Long, IndexJournalEntryData.Record>> actual = new ArrayList<>();

        reader.forEachDocIdRecord((url, word) -> actual.add(Pair.of(url, word)));
        assertEquals(expected, actual);
    }

}
