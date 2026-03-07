package nu.marginalia.btree.legacy;

public enum LegacyBTreeBlockSize {
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

    final int blockSizeBits;

    LegacyBTreeBlockSize(int blockSizeBits) {
        this.blockSizeBits = blockSizeBits;
    }

    public static LegacyBTreeBlockSize fromBitCount(int blockSizeBits) {
        for (var size : values()) {
            if (size.blockSizeBits == blockSizeBits)
                return size;
        }
        throw new IllegalArgumentException();
    }
}
