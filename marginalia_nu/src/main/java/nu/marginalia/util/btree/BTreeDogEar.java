package nu.marginalia.util.btree;

import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;

/*
 * End-of-page mark that's used as a sentinel to verify that
 * the BTreeWriter's caller actually writes as much as they say
 * they want to. (Failing to do so will corrupt the tree)
 *
 */
public class BTreeDogEar {

    private LongArray sentinelSlice;

    public BTreeDogEar(BTreeContext ctx, BTreeHeader header, LongArray base) {
        if (header.numEntries() > 3) {
            sentinelSlice = base.range(
                    (long) header.numEntries() * ctx.entrySize() - 3,
                    (long) header.numEntries() * ctx.entrySize());
            sentinelSlice.set(0, 4L);
            sentinelSlice.set(1, 5L);
            sentinelSlice.set(2, 1L);
        }
    }

    public boolean verify() {
        if (sentinelSlice == null)
            return true;

        return 4 != sentinelSlice.get(0) || 5 != sentinelSlice.get(1) || 1 != sentinelSlice.get(2);
    }

}
