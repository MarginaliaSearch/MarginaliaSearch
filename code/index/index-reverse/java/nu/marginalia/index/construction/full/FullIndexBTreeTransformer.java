package nu.marginalia.index.construction.full;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.algo.LongArrayTransformations;
import nu.marginalia.btree.BTreeWriter;
import nu.marginalia.btree.model.BTreeContext;

import java.io.IOException;

/** Constructs the BTrees in a reverse index */
public class FullIndexBTreeTransformer implements LongArrayTransformations.LongIOTransformer {
    private final BTreeWriter writer;
    private final int entrySize;
    private final LongArray documentsArray;

    long start = 0;
    long writeOffset = 0;

    public FullIndexBTreeTransformer(LongArray urlsFileMap,
                                     int entrySize,
                                     BTreeContext bTreeContext,
                                     LongArray documentsArray) {
        this.documentsArray = documentsArray;
        this.writer = new BTreeWriter(urlsFileMap, bTreeContext);
        this.entrySize = entrySize;
    }

    @Override
    public long transform(long pos, long end) throws IOException {

        final int size = (int) ((end - start) / entrySize);

        if (size == 0) {
            return -1;
        }

        final long offsetForBlock = writeOffset;

        writeOffset += writer.write(writeOffset, size,
                mapRegion -> mapRegion.transferFrom(documentsArray, start, 0, end - start)
        );

        start = end;
        return offsetForBlock;
    }
}
