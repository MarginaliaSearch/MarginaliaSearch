package nu.marginalia.btree.model;

public enum BTreeBlockSize {
    BS_16(1),
    BS_32(2),
    BS_64(3),
    BS_128(4),
    BS_256(5),
    BS_512(6),
    BS_1024(7),
    BS_2048(8),
    BS_4096(9),
    BS_8192(10);

    // blockSizeBits can be viewed as the number of logical branches at each index layer.
    // where 1 is the world's most over-engineered binary tree. In practice you want as many
    // as possible while staying below the page size limit.
    //
    // The formula for converting between the blockSizeBits as used in BTreeContext, and
    // the byte size on disk is (1<<blockSizeBits) * sizeof(long) [ * entrySize ]

    final int blockSizeBits;


    BTreeBlockSize(int blockSizeBits) {
        this.blockSizeBits = blockSizeBits;
    }

    public static BTreeBlockSize fromBitCount(int blockSizeBits) {
        for (var size : values()) {
            if (size.blockSizeBits == blockSizeBits)
                return size;
        }
        throw new IllegalArgumentException();
    }
}
