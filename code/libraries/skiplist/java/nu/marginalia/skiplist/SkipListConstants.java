package nu.marginalia.skiplist;

public class SkipListConstants {
    public static final int BLOCK_SIZE = Integer.getInteger("index.documentsSkipListBlockSize", 65536);
    static final int MIN_TRUNCATED_BLOCK_SIZE = Math.min(512, BLOCK_SIZE / 4);

    static final int HEADER_SIZE = 8;
    static final int SEGREGATED_HEADER_SIZE = 16;
    static final int RECORD_SIZE = 2;
    static final int MAX_RECORDS_PER_BLOCK = (BLOCK_SIZE/8 - 2)/RECORD_SIZE;

    static final byte FLAG_END_BLOCK = 1<<0;


    static int skipOffsetForPointer(int pointerIdx) {
        final int linearPart = 16;
        if (pointerIdx <= linearPart) {
            return pointerIdx + 1;
        }
        return linearPart + ((pointerIdx - linearPart - 1) * (pointerIdx - linearPart - 1));
    }

    static int numPointersForBlock(int blockIdx) {
        return 64;
    }

    static int numPointersForRootBlock(int n) {
        int numBlocks = estimateNumBlocks(n);
        for (int fp = 0;;fp++) {
            if (skipOffsetForPointer(fp) >= numBlocks) {
                return fp;
            }
        }
    }

    static int rootBlockCapacity(int rootBlockSize, int n) {
        return Math.min(n, (rootBlockSize - SEGREGATED_HEADER_SIZE - 8 * numPointersForRootBlock(n)) / (8*RECORD_SIZE));
    }

    static int nonRootBlockCapacity(int blockIdx) {
        assert blockIdx >= 1;
        return (BLOCK_SIZE - SEGREGATED_HEADER_SIZE - 8 * numPointersForBlock(blockIdx)) / (8*RECORD_SIZE);
    }

    static int estimateNumBlocks(int n) {
        return n / MAX_RECORDS_PER_BLOCK + Integer.signum(n % MAX_RECORDS_PER_BLOCK);
    }

    public static int pageDataOffset(int baseBlockOffset, int fc) {
        return baseBlockOffset + 8 * (1 + fc);
    }
}
