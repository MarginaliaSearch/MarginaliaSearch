package nu.marginalia.skiplist;

import nu.marginalia.skiplist.compression.DocIdCompressor;
import nu.marginalia.skiplist.compression.input.CompressorInput;

public class SkipListConstants {
    public static final int BLOCK_SIZE = Integer.getInteger("index.documentsSkipListBlockSize", 8192);
    public static final int VALUE_BLOCK_SIZE = Integer.getInteger("index.documentsSkipListValueBlockSize", 4096);

    static final int DATA_BLOCK_HEADER_SIZE = 16;
    static final int VALUE_BLOCK_HEADER_SIZE = 8;

    static final int RECORD_SIZE = 3;
    public static final int MAX_RECORDS_PER_BLOCK = (BLOCK_SIZE/8 - 3);

    static final int POINTER_TARGET_COUNT = 64;

    static final byte FLAG_END_BLOCK = 1<<0;
    static final byte FLAG_COMPACT_BLOCK = 1<<1;
    static final byte FLAG_VALUE_BLOCK = 1<<2;
    static final byte FLAG_FOOTER_BLOCK = 1<<3;
    static final byte FLAG_COMPRESSED_BLOCK = 1<<4;

    static int skipOffsetForPointer(int pointerIdx) {
        final int linearPart = 16;
        if (pointerIdx <= linearPart) {
            return pointerIdx + 1;
        }
        return linearPart + ((pointerIdx - linearPart - 1) * (pointerIdx - linearPart - 1));
    }


    static int numPointersForRootBlock(int rootBlockSize, StaggeredCompressorInput compressorInput) {
        int numBlocks = estimateNumBlocks(compressorInput);
        int fp;

        for (fp = 0; fp <= POINTER_TARGET_COUNT;fp++) {
            if (rootBlockCapacity(rootBlockSize, fp, compressorInput) <= 0
             || skipOffsetForPointer(fp) >= numBlocks)
                break;
        }

        return fp;
    }


    static int rootBlockCapacity(int rootBlockSize, int pointerCount, CompressorInput input) {
        return DocIdCompressor.calcMaxEntries(input,
                (rootBlockSize - DATA_BLOCK_HEADER_SIZE - 8 * pointerCount));
    }

    static int rootBlockCapacity(int rootBlockSize, StaggeredCompressorInput input) {

        return rootBlockCapacity(rootBlockSize,
                numPointersForRootBlock(rootBlockSize, input),
                input);
    }

    static int nonRootBlockCapacity(CompressorInput input) {
        int space = (BLOCK_SIZE - DATA_BLOCK_HEADER_SIZE - 8 * POINTER_TARGET_COUNT);
        return DocIdCompressor.calcMaxEntries(input, space);
    }

    static int estimateNumBlocks(StaggeredCompressorInput input) {
        int maxCapacityPerBlock = 8 * MAX_RECORDS_PER_BLOCK;
        var calcInput = StaggeredCompressorInput.copyOf(input);

        int blocks = 0;
        while (calcInput.size() > 0) {
            blocks++;
            calcInput.moveBounds(DocIdCompressor.calcMaxEntries(calcInput, maxCapacityPerBlock));
        }
        return blocks;
    }

    public static int pageDataOffset(int baseBlockOffset, int fc) {
        return baseBlockOffset + 8 * (2 + fc);
    }
}
