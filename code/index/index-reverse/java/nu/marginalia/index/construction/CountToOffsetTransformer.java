package nu.marginalia.index.construction;

import nu.marginalia.array.functional.LongTransformer;

/**
 * Transforms an array of item-counts into an array of item-offsets such that the previous counts would fit into an
 * array indexed by the generated array.<p>
 *
 * [ 1, 2, 3, 5, ... ] -> [ 0, 1, 3, 6, 11, ... ]
 *
 */
public class CountToOffsetTransformer implements LongTransformer {
    long offset = 0;

    public final int entrySize;

    public CountToOffsetTransformer(int entrySize) {
        this.entrySize = entrySize;
    }

    @Override
    public long transform(long pos, long count) {
        return (offset += entrySize * count);
    }
}
