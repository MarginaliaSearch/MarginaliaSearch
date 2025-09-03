package nu.marginalia.index.journal;

import nu.marginalia.sequence.slop.VarintCodedSequenceArrayColumn;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.array.LongArrayColumn;
import nu.marginalia.slop.column.primitive.IntColumn;
import nu.marginalia.slop.column.primitive.LongColumn;
import nu.marginalia.slop.column.string.EnumColumn;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public record IndexJournalPage(Path baseDir, int page) {
    public static IntColumn features = new IntColumn("features", StorageType.PLAIN);
    public static IntColumn size = new IntColumn("size", StorageType.PLAIN);
    public static LongColumn combinedId = new LongColumn("combinedId", StorageType.PLAIN);
    public static LongColumn documentMeta = new LongColumn("documentMeta", StorageType.PLAIN);

    public static LongArrayColumn termIds = new LongArrayColumn("termIds", StorageType.ZSTD);
    public static ByteArrayColumn termMeta = new ByteArrayColumn("termMetadata", StorageType.ZSTD);
    public static VarintCodedSequenceArrayColumn positions = new VarintCodedSequenceArrayColumn("termPositions", StorageType.ZSTD);

    public static ByteArrayColumn spanCodes = new ByteArrayColumn("spanCodes", StorageType.ZSTD);
    public static VarintCodedSequenceArrayColumn spans = new VarintCodedSequenceArrayColumn("spans", StorageType.ZSTD);

    public static EnumColumn language = new EnumColumn("language", StandardCharsets.US_ASCII, StorageType.PLAIN);

    public IndexJournalPage {
        if (!baseDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("Invalid base directory: " + baseDir);
        }
    }

    public LongColumn.Reader openCombinedId(SlopTable table) throws IOException {
        return combinedId.open(table);
    }

    public LongColumn.Reader openDocumentMeta(SlopTable table) throws IOException {
        return documentMeta.open(table);
    }

    public IntColumn.Reader openFeatures(SlopTable table) throws IOException {
        return features.open(table);
    }

    public IntColumn.Reader openSize(SlopTable table) throws IOException {
        return size.open(table);
    }

    public EnumColumn.Reader openLanguage(SlopTable table) throws IOException {
        return language.open(table);
    }

    public LongArrayColumn.Reader openTermIds(SlopTable table) throws IOException {
        return termIds.open(table);
    }

    public ByteArrayColumn.Reader openTermMetadata(SlopTable table) throws IOException {
        return termMeta.open(table);
    }

    public VarintCodedSequenceArrayColumn.Reader openTermPositions(SlopTable table) throws IOException {
        return positions.open(table);
    }

    public VarintCodedSequenceArrayColumn.Reader openSpans(SlopTable table) throws IOException {
        return spans.open(table);
    }

    public ByteArrayColumn.Reader openSpanCodes(SlopTable table) throws IOException {
        return spanCodes.open(table);
    }
}
