package nu.marginalia.index.journal;

import nu.marginalia.slop.column.array.ByteArrayColumnReader;
import nu.marginalia.slop.column.array.ByteArrayColumnWriter;
import nu.marginalia.slop.column.dynamic.GammaCodedSequenceReader;
import nu.marginalia.slop.column.dynamic.GammaCodedSequenceWriter;
import nu.marginalia.slop.column.dynamic.VarintColumnReader;
import nu.marginalia.slop.column.dynamic.VarintColumnWriter;
import nu.marginalia.slop.column.primitive.*;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.file.Path;

public record IndexJournalPage(Path baseDir, int page) {
    public static final ColumnDesc<IntColumnReader, IntColumnWriter> features = new ColumnDesc<>("features", ColumnType.INT_LE, StorageType.PLAIN);
    public static final ColumnDesc<IntColumnReader, IntColumnWriter> size = new ColumnDesc<>("size", ColumnType.INT_LE, StorageType.PLAIN);
    public static final ColumnDesc<LongColumnReader, LongColumnWriter> combinedId = new ColumnDesc<>("combinedId", ColumnType.LONG_LE, StorageType.PLAIN);
    public static final ColumnDesc<LongColumnReader, LongColumnWriter> documentMeta = new ColumnDesc<>("documentMeta", ColumnType.LONG_LE, StorageType.PLAIN);

    public static final ColumnDesc<VarintColumnReader, VarintColumnWriter> termCounts = new ColumnDesc<>("termCounts", ColumnType.VARINT_LE, StorageType.PLAIN);
    public static final ColumnDesc<LongColumnReader, LongColumnWriter> termIds = new ColumnDesc<>("termIds", ColumnType.LONG_LE, StorageType.ZSTD);
    public static final ColumnDesc<ByteColumnReader, ByteColumnWriter> termMeta = new ColumnDesc<>("termMetadata", ColumnType.BYTE, StorageType.ZSTD);
    public static final ColumnDesc<GammaCodedSequenceReader, GammaCodedSequenceWriter> positions = new ColumnDesc<>("termPositions", ColumnType.BYTE_ARRAY_GCS, StorageType.ZSTD);

    public static final ColumnDesc<ByteArrayColumnReader, ByteArrayColumnWriter> spanCodes = new ColumnDesc<>("spanCodes", ColumnType.BYTE_ARRAY, StorageType.ZSTD);
    public static final ColumnDesc<GammaCodedSequenceReader, GammaCodedSequenceWriter> spans = new ColumnDesc<>("spans", ColumnType.BYTE_ARRAY_GCS, StorageType.ZSTD);

    public IndexJournalPage {
        if (!baseDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("Invalid base directory: " + baseDir);
        }
    }

    public LongColumnReader openCombinedId() throws IOException {
        return combinedId.forPage(page).open(baseDir);
    }

    public LongColumnReader openDocumentMeta() throws IOException {
        return documentMeta.forPage(page).open(baseDir);
    }

    public IntColumnReader openFeatures() throws IOException {
        return features.forPage(page).open(baseDir);
    }

    public IntColumnReader openSize() throws IOException {
        return size.forPage(page).open(baseDir);
    }

    public LongColumnReader openTermCounts() throws IOException {
        return termCounts.forPage(page).open(baseDir);
    }

    public LongColumnReader openTermIds() throws IOException {
        return termIds.forPage(page).open(baseDir);
    }

    public ByteColumnReader openTermMetadata() throws IOException {
        return termMeta.forPage(page).open(baseDir);
    }

    public GammaCodedSequenceReader openTermPositions() throws IOException {
        return positions.forPage(page).open(baseDir);
    }

    public GammaCodedSequenceReader openSpans() throws IOException {
        return spans.forPage(page).open(baseDir);
    }

    public ByteArrayColumnReader openSpanCodes() throws IOException {
        return spanCodes.forPage(page).open(baseDir);
    }
}
