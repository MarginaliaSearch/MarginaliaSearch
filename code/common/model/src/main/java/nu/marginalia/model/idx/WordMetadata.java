package nu.marginalia.model.idx;


import nu.marginalia.bbpc.BrailleBlockPunchCards;

import java.util.EnumSet;
import java.util.Set;

import static java.lang.Math.max;
import static java.lang.Math.min;

public record WordMetadata(int tfIdf,
                           long positions,
                           byte flags) {

    // Bottom 16 bits are used for flags

    public static final long FLAGS_MASK = 0xFFL;

    public static final long TF_IDF_MASK = 0xFFL;
    public static final int TF_IDF_SCALE = 2;
    public static final int TF_IDF_SHIFT = 8;

    public static final int POSITIONS_SHIFT = 16;
    public static final long POSITIONS_MASK = 0xFFFF_FFFF_FFFFL;



    public WordMetadata() {
        this(emptyValue());
    }

    public WordMetadata(long value) {
        this(
                TF_IDF_SCALE * (int)((value >>> TF_IDF_SHIFT) & TF_IDF_MASK),
                ((value >>> POSITIONS_SHIFT) & POSITIONS_MASK),
                (byte) (value & FLAGS_MASK)
        );
    }

    public WordMetadata(int tfIdf,
                        long positions,
                        Set<WordFlags> flags)
    {
        this(tfIdf, positions, encodeFlags(flags));
    }

    private static byte encodeFlags(Set<WordFlags> flags) {
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
    public static long decodePositions(long meta) {
        return (meta >>> POSITIONS_SHIFT) & POSITIONS_MASK;
    }

    public static double decodeTfidf(long meta) {
        return TF_IDF_SCALE * ((meta >>> TF_IDF_SHIFT) & TF_IDF_MASK);
    }

    public boolean hasFlag(WordFlags flag) {
        return (flags & flag.asBit()) != 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('[')
                .append("tfidf=").append(tfIdf).append(", ")
                .append("positions=[").append(BrailleBlockPunchCards.printBits(positions, 48)).append('/').append(positions).append(']');
        sb.append(", flags=").append(flagSet()).append(']');
        return sb.toString();
    }

    /* Encoded in a 64 bit long
     */
    public long encode() {
        long ret = 0;

        ret |= Byte.toUnsignedLong(flags);
        ret |= min(TF_IDF_MASK, max(0, tfIdf / TF_IDF_SCALE)) << TF_IDF_SHIFT;
        ret |= (positions & POSITIONS_MASK) << POSITIONS_SHIFT;

        return ret;
    }

    public boolean isEmpty() {
        return positions == 0 && flags == 0 && tfIdf == 0;
    }

    public static long emptyValue() {
        return 0L;
    }


    public EnumSet<WordFlags> flagSet() {
        return WordFlags.decode(flags);
    }

    public int positionCount() {
        return Long.bitCount(positions);
    }
}
