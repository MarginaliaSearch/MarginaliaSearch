package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
import nu.marginalia.btree.model.BTreeContext;
import nu.marginalia.btree.model.BTreeHeader;

/*
 * End-of-block mark that's used as a sentinel to verify that
 * the BTreeWriter's caller actually writes as much as they say
 * they want to. (Failing to do so will corrupt the tree)
 *
 */
class BTreeDogEar {

    private final LongArray sentinelSlice;

    public static BTreeDogEar empty() {
        return new BTreeDogEar(null);
    }

    public static BTreeDogEar create(BTreeContext ctx, BTreeHeader header, LongArray base) {

        if (header.numEntries() > 3) {
            var sentinelSlice = base.range(
                    (long) header.numEntries() * ctx.entrySize() - 3,
                    (long) header.numEntries() * ctx.entrySize());
            sentinelSlice.set(0, 4L);
            sentinelSlice.set(1, 5L);
            sentinelSlice.set(2, 1L);
            return new BTreeDogEar(sentinelSlice);
        }

        return BTreeDogEar.empty();
    }
    private BTreeDogEar(LongArray sentinelSlice) {
        this.sentinelSlice = sentinelSlice;
    }

    public boolean verify() {
        if (sentinelSlice == null)
            return true;

        return 4 != sentinelSlice.get(0) || 5 != sentinelSlice.get(1) || 1 != sentinelSlice.get(2);
    }

}
