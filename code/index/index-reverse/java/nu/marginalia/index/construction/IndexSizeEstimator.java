package nu.marginalia.index.construction;

import nu.marginalia.array.functional.LongBinaryOperation;
import nu.marginalia.btree.model.BTreeContext;

/** Calculates the necessary size of an index from an array of offsets (@see CountToOffsetTransformer)<p>
 *
 * Used with LongArray.fold()
 * */
public class IndexSizeEstimator implements LongBinaryOperation {
    private final BTreeContext bTreeContext;
    private final int entrySize;

    public long size = 0;

    public IndexSizeEstimator(BTreeContext bTreeContext, int entrySize) {
        this.bTreeContext = bTreeContext;
        this.entrySize = entrySize;
    }

    @Override
    public long apply(long start, long end) {
        if (end == start) return end;

        size += bTreeContext.calculateSize((int) (end - start) / entrySize);

        return end;
    }
}
