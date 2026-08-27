package nu.marginalia.skiplist;

public enum SkipListFormat {
    V0(0) {
        // The V0 pointer layout has a monotonicity bug such that the quadratic part of the forward pointer table starts one
        // step too early.  This leads to redundant block traversal in some scenarios.

        // Supported for backward compatibility
        @Override
        public int skipOffsetForPointer(int pointerIdx) {
            if (pointerIdx <= LINEAR_POINTERS) {
                return pointerIdx + 1;
            }
            int q = pointerIdx - LINEAR_POINTERS - 1;
            return LINEAR_POINTERS + q * q;
        }
    },

    V1(1) { // Fixes monotonicity bug in V0
        @Override
        public int skipOffsetForPointer(int pointerIdx) {
            if (pointerIdx <= LINEAR_POINTERS) {
                return pointerIdx + 1;
            }
            int q = pointerIdx - LINEAR_POINTERS;
            return LINEAR_POINTERS + 1 + q * q;
        }
    };

    public static final SkipListFormat CURRENT = V1;
    private static final int LINEAR_POINTERS = 16;

    private final int version;

    SkipListFormat(int version) {
        this.version = version;
    }

    public int version() {
        return version;
    }

    public static SkipListFormat fromVersion(int version) {
        for (SkipListFormat format : values()) {
            if (format.version == version)
                return format;
        }
        throw new IllegalArgumentException("Unknown skip list format version " + version);
    }

    /** Distance in blocks from the block holding the forward pointer with index pointerIdx
     *  to the block whose largest value the pointer holds */
    public abstract int skipOffsetForPointer(int pointerIdx);
}
