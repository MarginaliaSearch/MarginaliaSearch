package nu.marginalia.wmsa.edge.index.svc.query.types;

import nu.marginalia.util.btree.BTreeQueryBuffer;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordMetadata;

import javax.annotation.Nullable;

import static java.lang.Math.min;
import static nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter.*;

public class EntrySourceFromBTree implements EntrySource {
    @Nullable
    private final BTreeReader reader;
    private final long metadataBitMask;
    private final Integer qualityLimit;

    public static final long NO_MASKING = ~0L;

    int pos;
    long endOffset;

    public EntrySourceFromBTree(@Nullable BTreeReader reader, long metadataBitMask, Integer qualityLimit) {
        this.reader = reader;
        this.metadataBitMask = metadataBitMask;
        this.qualityLimit = qualityLimit;

        if (reader != null) {
            pos = 0;
            endOffset = pos + (long) reader.numEntries() * ENTRY_SIZE;
        }
    }


    @Override
    public void skip(int n) {
        pos += n * ENTRY_SIZE;
    }

    @Override
    public void read(BTreeQueryBuffer buffer) {
        if (reader == null) {
            buffer.zero();
            return;
        }

        assert buffer.end%ENTRY_SIZE == 0;

        buffer.end = min(buffer.end, (int)(endOffset - pos));

        reader.readData(buffer.data, buffer.end, pos);

        pos += buffer.end;

        destagger(buffer);
        buffer.uniq();
    }

    private void destagger(BTreeQueryBuffer buffer) {
        if (metadataBitMask == NO_MASKING && qualityLimit == null) {
            for (int i = 0; (i + ENTRY_SIZE - 1) < buffer.end; i += ENTRY_SIZE) {
                buffer.data[i / ENTRY_SIZE] = buffer.data[i + ENTRY_URL_OFFSET];
            }

            buffer.end /= ENTRY_SIZE;
        }
        else {
            int write = 0;

            for (int read = 0; read < buffer.end; read+=ENTRY_SIZE) {
                final long metadata = buffer.data[read + ENTRY_METADATA_OFFSET];

                if (isQualityOk(metadata) && isFlagsOk(metadata)) {
                    buffer.data[write++] = buffer.data[read+ENTRY_URL_OFFSET];
                }
            }

            buffer.end = write;
        }
    }

    private boolean isFlagsOk(long metadata) {
        return metadataBitMask == ~0L || EdgePageWordMetadata.hasFlags(metadata, metadataBitMask);
    }

    private boolean isQualityOk(long metadata) {
        if (qualityLimit == null)
            return true;

        final int quality = EdgePageWordMetadata.decodeQuality(metadata);

        if (qualityLimit < 0)
            return quality > -qualityLimit;
        else
            return quality < qualityLimit;
    }

    @Override
    public boolean hasMore() {
        return pos < endOffset;
    }

    @Override
    public String toString() {
        return String.format("BTreeRange.EntrySource(@" + pos + ": " + endOffset + ")");
    }

}
