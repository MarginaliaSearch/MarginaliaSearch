package nu.marginalia.index.journal;

import nu.marginalia.sequence.slop.GammaCodedSequenceArrayColumn;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.array.LongArrayColumn;
import nu.marginalia.slop.column.primitive.IntColumn;
import nu.marginalia.slop.column.primitive.LongColumn;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.file.Path;

public record IndexJournalPage(Path baseDir, int page) {
    public static IntColumn features = new IntColumn("features", StorageType.PLAIN);
    public static IntColumn size = new IntColumn("size", StorageType.PLAIN);
    public static LongColumn combinedId = new LongColumn("combinedId", StorageType.PLAIN);
    public static LongColumn documentMeta = new LongColumn("documentMeta", StorageType.PLAIN);

    public static LongArrayColumn termIds = new LongArrayColumn("termIds", StorageType.ZSTD);
    public static ByteArrayColumn termMeta = new ByteArrayColumn("termMetadata", StorageType.ZSTD);
    public static GammaCodedSequenceArrayColumn positions = new GammaCodedSequenceArrayColumn("termPositions", StorageType.ZSTD);

    public static ByteArrayColumn spanCodes = new ByteArrayColumn("spanCodes", StorageType.ZSTD);
    public static GammaCodedSequenceArrayColumn spans = new GammaCodedSequenceArrayColumn("spans", StorageType.ZSTD);

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


    public LongArrayColumn.Reader openTermIds(SlopTable table) throws IOException {
        return termIds.open(table);
    }

    public ByteArrayColumn.Reader openTermMetadata(SlopTable table) throws IOException {
        return termMeta.open(table);
    }

    public GammaCodedSequenceArrayColumn.Reader openTermPositions(SlopTable table) throws IOException {
        return positions.open(table);
    }

    public GammaCodedSequenceArrayColumn.Reader openSpans(SlopTable table) throws IOException {
        return spans.open(table);
    }

    public ByteArrayColumn.Reader openSpanCodes(SlopTable table) throws IOException {
        return spanCodes.open(table);
    }
}
