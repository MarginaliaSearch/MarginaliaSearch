package nu.marginalia.index.config;

public class ForwardIndexParameters {
    public static final int METADATA_OFFSET = 0;
    public static final int FEATURES_OFFSET = 1;
    public static final int SPANS_OFFSET = 2;
    public static final int DOC_TEXT_OFFSET = 3;

    public static final long FOOTER_MAGIC_MASK = 0xFFFF_FFFF_FFFF_0000L;
    public static final long FOOTER_MAGIC_WORD = 0xF030_83DF_0073_0000L;
    public static final long FOOTER_VERSION_MASK = ~FOOTER_MAGIC_MASK;

    public enum ForwardIndexVersion {
        VERSION_LEGACY(3),
        V2026_07__1(3), // split 32 bit 'size' part of features into 16 bits for size, 16 bits for pub date
        V2026_08__1(4)  // add document texts pointer
        ;

        // CAVEAT: If entry size grows beyond 4, we may need to update the assumptions in RankingBatchFetcher as it
        // skips retrieval of the document text pointer.

        public int entrySize;

        ForwardIndexVersion(int entrySize) {
            this.entrySize = entrySize;
        }
    }

    public static long encodeFooter(ForwardIndexVersion version) {
        assert version.ordinal() == 0 || (version.ordinal() & FOOTER_VERSION_MASK) != 0;  // We've really gone through 32,768 versions?!

        return FOOTER_MAGIC_WORD | (version.ordinal());
    }

    public static ForwardIndexVersion decodeVersion(long footer) {
        if ((footer & FOOTER_MAGIC_MASK) != FOOTER_MAGIC_WORD) {
            return ForwardIndexVersion.VERSION_LEGACY;
        }
        return ForwardIndexVersion.values()[(int) (footer & FOOTER_VERSION_MASK)];
    }

}
