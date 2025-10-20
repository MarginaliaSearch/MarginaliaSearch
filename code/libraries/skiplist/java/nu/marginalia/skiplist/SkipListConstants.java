package nu.marginalia.skiplist;

public class SkipListConstants {
    public static final int BLOCK_SIZE = Integer.getInteger("index.documentsSkipListBlockSize", 32768);

    static final int SKIP_LIST_VERSION = 0;

    static final int DATA_BLOCK_HEADER_SIZE = 8;
    static final int VALUE_BLOCK_HEADER_SIZE = 8;

    static final int RECORD_SIZE = 3;
    public static final int MAX_RECORDS_PER_BLOCK = (BLOCK_SIZE/8 - 2);

    static final int POINTER_TARGET_COUNT = 64;

    static final byte FLAG_END_BLOCK = 1<<0;
    static final byte FLAG_COMPACT_BLOCK = 1<<1;
    static final byte FLAG_VALUE_BLOCK = 1<<2;
    static final byte FLAG_FOOTER_BLOCK = 1<<3;

    static int skipOffsetForPointer(int pointerIdx) {
        final int linearPart = 16;
        if (pointerIdx <= linearPart) {
            return pointerIdx + 1;
        }
        return linearPart + ((pointerIdx - linearPart - 1) * (pointerIdx - linearPart - 1));
    }

    static int numPointersForBlock(int blockIdx) {
        return POINTER_TARGET_COUNT;
    }

    static int numPointersForRootBlock(int rootBlockSize, int numItems) {
        int numBlocks = estimateNumBlocks(numItems);
        int fp;

        for (fp = 0; fp <= POINTER_TARGET_COUNT;fp++) {
            if (rootBlockCapacity(rootBlockSize, fp, numItems) <= 0
             || skipOffsetForPointer(fp) >= numBlocks)
                break;
        }

        return fp;
    }


    static int rootBlockCapacity(int rootBlockSize, int pointerCount, int n) {
        return Math.min(n, (rootBlockSize - DATA_BLOCK_HEADER_SIZE - 8 * pointerCount) / 8);
    }

    static int rootBlockCapacity(int rootBlockSize, int n) {
        return rootBlockCapacity(rootBlockSize, numPointersForRootBlock(rootBlockSize, n), n);
    }

    static int nonRootBlockCapacity(int blockIdx) {
        assert blockIdx >= 1;
        return (BLOCK_SIZE - DATA_BLOCK_HEADER_SIZE - 8 * numPointersForBlock(blockIdx)) / 8;
    }

    static int estimateNumBlocks(int n) {
        return n / MAX_RECORDS_PER_BLOCK + Integer.signum(n % MAX_RECORDS_PER_BLOCK);
    }

    public static int pageDataOffset(int baseBlockOffset, int fc) {
        return baseBlockOffset + 8 * (1 + fc);
    }
}
