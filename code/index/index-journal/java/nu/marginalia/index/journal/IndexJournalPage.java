package nu.marginalia.index.journal;

import nu.marginalia.sequence.slop.GammaCodedSequenceColumn;
import nu.marginalia.sequence.slop.GammaCodedSequenceReader;
import nu.marginalia.sequence.slop.GammaCodedSequenceWriter;
import nu.marginalia.slop.column.array.ByteArrayColumnReader;
import nu.marginalia.slop.column.array.ByteArrayColumnWriter;
import nu.marginalia.slop.column.dynamic.VarintColumnReader;
import nu.marginalia.slop.column.dynamic.VarintColumnWriter;
import nu.marginalia.slop.column.primitive.*;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.SlopTable;
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
    public static final ColumnDesc<GammaCodedSequenceReader, GammaCodedSequenceWriter> positions = new ColumnDesc<>("termPositions", GammaCodedSequenceColumn.TYPE, StorageType.ZSTD);

    public static final ColumnDesc<ByteArrayColumnReader, ByteArrayColumnWriter> spanCodes = new ColumnDesc<>("spanCodes", ColumnType.BYTE_ARRAY, StorageType.ZSTD);
    public static final ColumnDesc<GammaCodedSequenceReader, GammaCodedSequenceWriter> spans = new ColumnDesc<>("spans", GammaCodedSequenceColumn.TYPE, StorageType.ZSTD);

    public IndexJournalPage {
        if (!baseDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("Invalid base directory: " + baseDir);
        }
    }

    public LongColumnReader openCombinedId(SlopTable table) throws IOException {
        return combinedId.forPage(page).open(table, baseDir);
    }

    public LongColumnReader openDocumentMeta(SlopTable table) throws IOException {
        return documentMeta.forPage(page).open(table, baseDir);
    }

    public IntColumnReader openFeatures(SlopTable table) throws IOException {
        return features.forPage(page).open(table, baseDir);
    }

    public IntColumnReader openSize(SlopTable table) throws IOException {
        return size.forPage(page).open(table, baseDir);
    }

    public LongColumnReader openTermCounts(SlopTable table) throws IOException {
        return termCounts.forPage(page).open(table, baseDir);
    }

    public LongColumnReader openTermIds(SlopTable table) throws IOException {
        return termIds.forPage(page).open(table.columnGroup("keywords"), baseDir);
    }

    public ByteColumnReader openTermMetadata(SlopTable table) throws IOException {
        return termMeta.forPage(page).open(table.columnGroup("keywords"), baseDir);
    }

    public GammaCodedSequenceReader openTermPositions(SlopTable table) throws IOException {
        return positions.forPage(page).open(table.columnGroup("keywords"), baseDir);
    }

    public GammaCodedSequenceReader openSpans(SlopTable table) throws IOException {
        return spans.forPage(page).open(table.columnGroup("spans"), baseDir);
    }

    public ByteArrayColumnReader openSpanCodes(SlopTable table) throws IOException {
        return spanCodes.forPage(page).open(table.columnGroup("spans"), baseDir);
    }
}
