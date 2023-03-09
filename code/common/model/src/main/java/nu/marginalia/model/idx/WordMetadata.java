package nu.marginalia.model.idx;

import nu.marginalia.model.crawl.EdgePageWordFlags;
import nu.marginalia.util.BrailleBlockPunchCards;

import java.util.EnumSet;
import java.util.Set;

import static java.lang.Math.max;
import static java.lang.Math.min;

public record WordMetadata(int tfIdf,
                           int positions,
                           int count,
                           byte flags) {
    public WordMetadata {
        if (WordMetadata.class.desiredAssertionStatus()) {
            if (Integer.bitCount(positions) > count) {
                System.err.println(Integer.bitCount(positions) + ">" + count);
            }
        }
    }

    public static final long COUNT_MASK = 0xFL;
    public static final int COUNT_SHIFT = 8;

    public static final long TF_IDF_MASK = 0xFFFFL;
    public static final int TF_IDF_SHIFT = 16;

    public static final int POSITIONS_SHIFT = 32;
    public static final long POSITIONS_MASK = 0xFFFF_FFFFL;

    public static final long FLAGS_MASK = 0xFF;


    public WordMetadata() {
        this(emptyValue());
    }

    public WordMetadata(long value) {
        this(
                (int)((value >>> TF_IDF_SHIFT) & TF_IDF_MASK),
                (int)((value >>> POSITIONS_SHIFT) & POSITIONS_MASK),
                Math.max((int)((value >>> POSITIONS_SHIFT) & POSITIONS_MASK), (int)((value >>> COUNT_SHIFT) & COUNT_MASK)),
                (byte) (value & FLAGS_MASK)
        );
    }

    public WordMetadata(int tfIdf,
                        int positions,
                        int count,
                        Set<EdgePageWordFlags> flags)
    {
        this(tfIdf, positions, count, encodeFlags(flags));
    }

    private static byte encodeFlags(Set<EdgePageWordFlags> flags) {
        byte ret = 0;
        for (var flag : flags) { ret |= flag.asBit(); }
        return ret;
    }

    public static boolean hasFlags(long encoded, long metadataBitMask) {
        return (encoded & metadataBitMask) == metadataBitMask;
    }
    public static boolean hasAnyFlags(long encoded, long metadataBitMask) {
        return (encoded & metadataBitMask) != 0;
    }
    public static int decodePositions(long meta) {
        return (int) (meta >>> POSITIONS_SHIFT);
    }

    public static double decodeTfidf(long meta) {
        return (meta >>> TF_IDF_SHIFT) & TF_IDF_MASK;
    }

    public boolean hasFlag(EdgePageWordFlags flag) {
        return (flags & flag.asBit()) != 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('[')
                .append("tfidf=").append(tfIdf).append(", ")
                .append("count=").append(count).append(", ")
                .append("positions=[").append(BrailleBlockPunchCards.printBits(positions, 32)).append(']');
        sb.append(", flags=").append(flags).append(']');
        return sb.toString();
    }

    /* Encoded in a 64 bit long
     */
    public long encode() {
        long ret = 0;

        ret |= Byte.toUnsignedLong(flags);
        ret |= min(TF_IDF_MASK, max(0, tfIdf)) << TF_IDF_SHIFT;
        ret |= min(COUNT_MASK, max(0, count)) << COUNT_SHIFT;
        ret |= ((long)(positions)) << POSITIONS_SHIFT;

        return ret;
    }

    public boolean isEmpty() {
        return count == 0 && positions == 0 && flags == 0 && tfIdf == 0;
    }

    public static long emptyValue() {
        return 0L;
    }


    public EnumSet<EdgePageWordFlags> flagSet() {
        return EdgePageWordFlags.decode(flags);
    }
}
