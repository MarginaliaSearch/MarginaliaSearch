package nu.marginalia.index.construction;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.index.journal.reader.IndexJournalReaderSingleCompressedFile;
import nu.marginalia.index.journal.writer.IndexJournalWriterSingleFileImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestJournalFactory {
    Path tempDir = Files.createTempDirectory("journal");

    public TestJournalFactory() throws IOException {}

    public void clear() throws IOException {
        List<Path> toDelete = new ArrayList<>();
        try (var dirStream = Files.list(tempDir)) {
            dirStream.forEach(toDelete::add);
        }
        for (var tempFile : toDelete) {
            Files.delete(tempFile);
        }
        Files.delete(tempDir);
    }

    public record EntryData(long docId, long docMeta, long... wordIds) {
        @Override
        public String toString() {
            return "EntryData{" +
                    "docId=" + docId +
                    ", docMeta=" + docMeta +
                    ", wordIds=" + Arrays.toString(wordIds) +
                    '}';
        }
    }
    public record EntryDataWithWordMeta(long docId, long docMeta, WordWithMeta... wordIds) {
        @Override
        public String toString() {
            return "EntryDataWithWordMeta{" +
                    "docId=" + docId +
                    ", docMeta=" + docMeta +
                    ", wordIds=" + Arrays.toString(wordIds) +
                    '}';
        }
    }
    public record WordWithMeta(long wordId, long meta) {}

    public static WordWithMeta wm(long wordId, long meta) {
        return new WordWithMeta(wordId, meta);
    }

    IndexJournalReader createReader(EntryData... entries) throws IOException {
        Path jf = Files.createTempFile(tempDir, "journal", ".dat");

        var writer = new IndexJournalWriterSingleFileImpl(jf);
        for (var entry : entries) {
            long[] data = new long[entry.wordIds.length * 2];
            for (int i = 0; i < entry.wordIds.length; i++)
                data[i*2] = entry.wordIds[i];

            writer.put(new IndexJournalEntryHeader(entries.length, 0, entry.docId, entry.docMeta),
                    new IndexJournalEntryData(data));
        }
        writer.close();
        var ret = new IndexJournalReaderSingleCompressedFile(jf);
        return ret;
    }

    public IndexJournalReader createReader(EntryDataWithWordMeta... entries) throws IOException {
        Path jf = Files.createTempFile(tempDir, "journal", ".dat");

        var writer = new IndexJournalWriterSingleFileImpl(jf);
        for (var entry : entries) {
            long[] data = new long[entry.wordIds.length * 2];
            for (int i = 0; i < entry.wordIds.length; i++) {
                data[i * 2] = entry.wordIds[i].wordId;
                data[i * 2 + 1] = entry.wordIds[i].meta;
            }

            writer.put(new IndexJournalEntryHeader(entries.length, 0, entry.docId, entry.docMeta),
                    new IndexJournalEntryData(data));
        }
        writer.close();
        var ret = new IndexJournalReaderSingleCompressedFile(jf);
        return ret;
    }
}
