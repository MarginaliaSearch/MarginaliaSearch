package nu.marginalia.index.journal;

import nu.marginalia.sequence.slop.GammaCodedSequenceArrayColumn;
import nu.marginalia.sequence.slop.GammaCodedSequenceArrayReader;
import nu.marginalia.sequence.slop.GammaCodedSequenceArrayWriter;
import nu.marginalia.slop.ColumnTypes;
import nu.marginalia.slop.column.array.ByteArrayColumnReader;
import nu.marginalia.slop.column.array.ByteArrayColumnWriter;
import nu.marginalia.slop.column.array.LongArrayColumnReader;
import nu.marginalia.slop.column.array.LongArrayColumnWriter;
import nu.marginalia.slop.column.primitive.IntColumnReader;
import nu.marginalia.slop.column.primitive.IntColumnWriter;
import nu.marginalia.slop.column.primitive.LongColumnReader;
import nu.marginalia.slop.column.primitive.LongColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.SlopTable;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.file.Path;

public record IndexJournalPage(Path baseDir, int page) {
    public static final ColumnDesc<IntColumnReader, IntColumnWriter> features = new ColumnDesc<>("features", ColumnTypes.INT_LE, StorageType.PLAIN);
    public static final ColumnDesc<IntColumnReader, IntColumnWriter> size = new ColumnDesc<>("size", ColumnTypes.INT_LE, StorageType.PLAIN);
    public static final ColumnDesc<LongColumnReader, LongColumnWriter> combinedId = new ColumnDesc<>("combinedId", ColumnTypes.LONG_LE, StorageType.PLAIN);
    public static final ColumnDesc<LongColumnReader, LongColumnWriter> documentMeta = new ColumnDesc<>("documentMeta", ColumnTypes.LONG_LE, StorageType.PLAIN);

    public static final ColumnDesc<LongArrayColumnReader, LongArrayColumnWriter> termIds = new ColumnDesc<>("termIds", ColumnTypes.LONG_ARRAY_LE, StorageType.ZSTD);
    public static final ColumnDesc<ByteArrayColumnReader, ByteArrayColumnWriter> termMeta = new ColumnDesc<>("termMetadata", ColumnTypes.BYTE_ARRAY, StorageType.ZSTD);
    public static final ColumnDesc<GammaCodedSequenceArrayReader, GammaCodedSequenceArrayWriter> positions = new ColumnDesc<>("termPositions", GammaCodedSequenceArrayColumn.TYPE, StorageType.ZSTD);

    public static final ColumnDesc<ByteArrayColumnReader, ByteArrayColumnWriter> spanCodes = new ColumnDesc<>("spanCodes", ColumnTypes.BYTE_ARRAY, StorageType.ZSTD);
    public static final ColumnDesc<GammaCodedSequenceArrayReader, GammaCodedSequenceArrayWriter> spans = new ColumnDesc<>("spans", GammaCodedSequenceArrayColumn.TYPE, StorageType.ZSTD);

    public IndexJournalPage {
        if (!baseDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("Invalid base directory: " + baseDir);
        }
    }

    public LongColumnReader openCombinedId(SlopTable table) throws IOException {
        return combinedId.open(table, baseDir);
    }

    public LongColumnReader openDocumentMeta(SlopTable table) throws IOException {
        return documentMeta.open(table, baseDir);
    }

    public IntColumnReader openFeatures(SlopTable table) throws IOException {
        return features.open(table, baseDir);
    }

    public IntColumnReader openSize(SlopTable table) throws IOException {
        return size.open(table, baseDir);
    }


    public LongArrayColumnReader openTermIds(SlopTable table) throws IOException {
        return termIds.open(table, baseDir);
    }

    public ByteArrayColumnReader openTermMetadata(SlopTable table) throws IOException {
        return termMeta.open(table, baseDir);
    }

    public GammaCodedSequenceArrayReader openTermPositions(SlopTable table) throws IOException {
        return positions.open(table, baseDir);
    }

    public GammaCodedSequenceArrayReader openSpans(SlopTable table) throws IOException {
        return spans.open(table, baseDir);
    }

    public ByteArrayColumnReader openSpanCodes(SlopTable table) throws IOException {
        return spanCodes.open(table, baseDir);
    }
}
