package nu.marginalia.index.construction.prio;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.algo.LongArrayTransformations;
import nu.marginalia.btree.BTreeWriter;
import nu.marginalia.btree.model.BTreeContext;

import java.io.IOException;
import java.nio.channels.FileChannel;

/** Constructs the BTrees in a reverse index */
public class PrioIndexBTreeTransformer implements LongArrayTransformations.LongIOTransformer {
    private final BTreeWriter writer;
    private final FileChannel intermediateChannel;

    private final int entrySize;

    long start = 0;
    long writeOffset = 0;

    public PrioIndexBTreeTransformer(LongArray urlsFileMap,
                                     int entrySize,
                                     BTreeContext bTreeContext,
                                     FileChannel intermediateChannel) {
        this.writer = new BTreeWriter(urlsFileMap, bTreeContext);
        this.entrySize = entrySize;
        this.intermediateChannel = intermediateChannel;
    }

    @Override
    public long transform(long pos, long end) throws IOException {

        final int size = (int) ((end - start) / entrySize);

        if (size == 0) {
            return -1;
        }

        final long offsetForBlock = writeOffset;

        writeOffset += writer.write(writeOffset, size,
                mapRegion -> mapRegion.transferFrom(intermediateChannel, start, 0, end - start)
        );

        start = end;
        return offsetForBlock;
    }
}
