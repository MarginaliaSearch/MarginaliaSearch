package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.index.journal.IndexJournalPage;
import nu.marginalia.index.journal.IndexJournalSlopWriter;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.model.processed.SlopDocumentRecord;
import nu.marginalia.sequence.VarintCodedSequence;
import nu.marginalia.test.TestUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TestJournalFactory {
    Path tempDir = Files.createTempDirectory("journal");

    public TestJournalFactory() throws IOException {}

    public void clear() throws IOException {
        TestUtil.clearTempDir(tempDir);
    }

    public record EntryData(long docId, long docMeta, String... wordIds) {
        public EntryData(long docId, long docMeta, long... wordIds) {
            this(docId, docMeta, Arrays.stream(wordIds).mapToObj(String::valueOf).toArray(String[]::new));
        }
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
    public record WordWithMeta(String wordId, byte meta, VarintCodedSequence gcs) {
        public WordWithMeta(long wordId, byte meta, VarintCodedSequence gcs) {
            this(String.valueOf(wordId), meta, gcs);
        }
    }

    public static WordWithMeta wm(long wordId, int meta, int... positions) {
        return new WordWithMeta(wordId, (byte) meta, VarintCodedSequence.generate(positions));
    }

    public IndexJournalPage createReader(EntryData... entries) throws IOException {
        Path ji = Files.createTempDirectory(tempDir, "journal");

        var writer = new IndexJournalSlopWriter(ji, 0);
        for (var entry : entries) {
            String[] termIds = new String[entry.wordIds.length];
            long[] meta = new long[entry.wordIds.length];

            VarintCodedSequence[] positions = new VarintCodedSequence[entry.wordIds.length];
            for (int i = 0; i < entry.wordIds.length; i++) {
                termIds[i] = entry.wordIds[i];
                meta[i] = 0;
                positions[i] = VarintCodedSequence.generate();
            }

            writer.put(
                    entry.docId,
                    new SlopDocumentRecord.KeywordsProjection(
                            "test",
                            -1,
                            0,
                            entry.docMeta,
                            15,
                            "en",
                            Arrays.asList(termIds),
                            meta,
                            Arrays.asList(positions),
                            new byte[0],
                            List.of()
                    ),
                    new KeywordHasher.AsciiIsh());
        }
        writer.close();

        return new IndexJournalPage(ji, 0);
    }

    public IndexJournalPage createReader(EntryDataWithWordMeta... entries) throws IOException {
        Path ji = Files.createTempDirectory(tempDir, "journal");

        var writer = new IndexJournalSlopWriter(ji, 0);
        for (var entry : entries) {

            String[] termIds = new String[entry.wordIds.length];
            long[] meta = new long[entry.wordIds.length];
            VarintCodedSequence[] positions = new VarintCodedSequence[entry.wordIds.length];
            for (int i = 0; i < entry.wordIds.length; i++) {
                termIds[i] = entry.wordIds[i].wordId;
                meta[i] = entry.wordIds[i].meta;
                positions[i] = Objects.requireNonNullElseGet(entry.wordIds[i].gcs, VarintCodedSequence::generate);
            }

            writer.put(
                    entry.docId,
                    new SlopDocumentRecord.KeywordsProjection(
                            "test",
                            -1,
                            0,
                            entry.docMeta,
                            15,
                            "en",
                            Arrays.asList(termIds),
                            meta,
                            Arrays.asList(positions),
                            new byte[0],
                            List.of()
                    ),
                    new KeywordHasher.AsciiIsh());

        }
        writer.close();

        return new IndexJournalPage(ji, 0);
    }
}
