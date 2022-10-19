package nu.marginalia.wmsa.edge.index.model;

import nu.marginalia.util.BrailleBlockPunchCards;

import java.util.EnumSet;

import static java.lang.Math.max;
import static java.lang.Math.min;

public record EdgePageWordMetadata(int tfIdf,
                                   int positions,
                                   int quality,
                                   int count,
                                   EnumSet<EdgePageWordFlags> flags) {

    // If flags are moved from the least significant end of
    // this struct, then EntrySourceFromBTree will break.

    public static final long COUNT_MASK = 0xFL;
    public static final int COUNT_SHIFT = 8;

    public static final long QUALITY_MASK = 0xFL;
    public static final int QUALITY_SHIFT = 12;

    public static final long TF_IDF_MASK = 0xFFFFL;
    public static final int TF_IDF_SHIFT = 16;

    public static final int POSITIONS_SHIFT = 32;

    public EdgePageWordMetadata(long value) {
        this(
                (int)((value >>> TF_IDF_SHIFT) & TF_IDF_MASK),
                (int)(value >>> POSITIONS_SHIFT),
                (int)((value >>> QUALITY_SHIFT) & QUALITY_MASK),
                (int)((value >>> COUNT_SHIFT) & COUNT_MASK),
                EdgePageWordFlags.decode(value)
        );
    }

    public static int decodeQuality(long encoded) {
        return (int)((encoded >>> QUALITY_SHIFT) & QUALITY_MASK);
    }

    public static boolean hasFlags(long encoded, long metadataBitMask) {
        return (encoded & metadataBitMask) == encoded;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('[')
                .append("tfidf=").append(tfIdf).append(", ")
                .append("quality=").append(quality).append(", ")
                .append("count=").append(count).append(", ")
                .append("positions=[").append(BrailleBlockPunchCards.printBits(positions, 32)).append(']');
        sb.append(", flags=").append(flags).append(']');
        return sb.toString();
    }

    /* Encoded in a 64 bit long as
       0-8 flags
       8-12 count,
       12-16 quality,
       16-32 tf-idf [0, 65536]
       32-64 position mask
     */
    public long encode() {
        long ret = 0;

        for (var flag : flags) {
            ret |= flag.asBit();
        }

        ret |= min(TF_IDF_MASK, max(0, tfIdf)) << TF_IDF_SHIFT;
        ret |= min(COUNT_MASK, max(0, count)) << COUNT_SHIFT;
        ret |= min(QUALITY_MASK, max(0, quality)) << QUALITY_SHIFT;
        ret |= ((long)(positions)) << POSITIONS_SHIFT;

        return ret;
    }

    public boolean isEmpty() {
        return count == 0 && positions == 0 && flags.isEmpty() && tfIdf == 0;
    }

    public static long emptyValue() {
        return 0L;
    }


}
