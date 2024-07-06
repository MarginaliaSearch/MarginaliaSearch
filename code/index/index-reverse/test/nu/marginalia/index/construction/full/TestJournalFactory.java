package nu.marginalia.index.construction.full;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.index.journal.reader.IndexJournalReaderSingleFile;
import nu.marginalia.index.journal.writer.IndexJournalWriterSingleFileImpl;
import nu.marginalia.sequence.GammaCodedSequence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    public record WordWithMeta(long wordId, long meta, GammaCodedSequence gcs) {}

    public static WordWithMeta wm(long wordId, long meta, int... positions) {
        return new WordWithMeta(wordId, meta, GammaCodedSequence.generate(ByteBuffer.allocate(128), positions));
    }

    IndexJournalReader createReader(EntryData... entries) throws IOException {
        Path jf = Files.createTempFile(tempDir, "journal", ".dat");

        var writer = new IndexJournalWriterSingleFileImpl(jf);
        for (var entry : entries) {
            long[] termIds = new long[entry.wordIds.length];
            long[] meta = new long[entry.wordIds.length];

            GammaCodedSequence[] positions = new GammaCodedSequence[entry.wordIds.length];
            for (int i = 0; i < entry.wordIds.length; i++) {
                termIds[i] = entry.wordIds[i];
                meta[i] = 0;
                positions[i] = new GammaCodedSequence(new byte[1]);
            }

            writer.put(new IndexJournalEntryHeader(entries.length, 0, 15, entry.docId, entry.docMeta),
                    new IndexJournalEntryData(termIds, meta, positions));
        }
        writer.close();
        var ret = new IndexJournalReaderSingleFile(jf);
        return ret;
    }

    public IndexJournalReader createReader(EntryDataWithWordMeta... entries) throws IOException {
        Path jf = Files.createTempFile(tempDir, "journal", ".dat");

        var writer = new IndexJournalWriterSingleFileImpl(jf);
        for (var entry : entries) {

            long[] termIds = new long[entry.wordIds.length];
            long[] meta = new long[entry.wordIds.length];
            GammaCodedSequence[] positions = new GammaCodedSequence[entry.wordIds.length];
            for (int i = 0; i < entry.wordIds.length; i++) {
                termIds[i] = entry.wordIds[i].wordId;
                meta[i] = entry.wordIds[i].meta;
                positions[i] = Objects.requireNonNullElseGet(entry.wordIds[i].gcs, () -> new GammaCodedSequence(new byte[1]));
            }

            writer.put(new IndexJournalEntryHeader(entries.length, 0, 15, entry.docId, entry.docMeta),
                    new IndexJournalEntryData(termIds, meta, positions));
        }
        writer.close();
        var ret = new IndexJournalReaderSingleFile(jf);
        return ret;
    }
}
