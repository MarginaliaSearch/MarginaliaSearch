package nu.marginalia.index.config;

import nu.marginalia.btree.legacy.LegacyBTreeBlockSize;
import nu.marginalia.btree.legacy.LegacyBTreeContext;

public class ReverseIndexParameters
{
    public static final LegacyBTreeContext wordsBTreeContext = new LegacyBTreeContext(5, 2, LegacyBTreeBlockSize.BS_512);

    /** Page size in bytes for the paged B-tree writer. */
    public static final int BTREE_PAGE_SIZE_BYTES = 4096;

    /** Entry size (longs per entry) for the words B-tree. */
    public static final int BTREE_ENTRY_SIZE = 2;

    /** Whether to use the legacy B-tree implementation for writing. */
    public static boolean useLegacyBTree() {
        return Boolean.getBoolean("index.useLegacyBTree");
    }
}
