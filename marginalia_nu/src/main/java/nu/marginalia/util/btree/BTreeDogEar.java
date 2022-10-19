package nu.marginalia.util.btree;

import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLongSlice;

/*
 * End-of-page mark that's used as a sentinel to verify that
 * the BTreeWriter's caller actually writes as much as they say
 * they want to. (Failing to do so will corrupt the tree)
 *
 */
public class BTreeDogEar {

    private MultimapFileLongSlice sentinelSlice;

    public BTreeDogEar(BTreeContext ctx, BTreeHeader header, MultimapFileLongSlice base) {
        if (header.numEntries() > 3) {
            sentinelSlice = base.atOffset((long) header.numEntries() * ctx.entrySize() - 3);
            sentinelSlice.put(0, 4L);
            sentinelSlice.put(1, 5L);
            sentinelSlice.put(2, 1L);
        }
    }

    public boolean verify() {
        if (sentinelSlice == null)
            return true;

        return 4 != sentinelSlice.get(0) || 5 != sentinelSlice.get(1) || 1 != sentinelSlice.get(2);
    }

}
