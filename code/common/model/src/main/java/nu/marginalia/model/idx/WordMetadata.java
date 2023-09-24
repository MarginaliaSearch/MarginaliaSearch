package nu.marginalia.model.idx;


import nu.marginalia.bbpc.BrailleBlockPunchCards;

import java.util.EnumSet;
import java.util.Set;

/** Word level metadata designed to fit in a single 64 bit long.
 *
 * @param positions bitmask of term positions within the document
 * @param flags word flags (see {@link WordFlags})
 */
public record WordMetadata(long positions,
                           byte flags) {

    // Bottom 8 bits are used for flags

    public static final long FLAGS_MASK = 0xFFL;

    public static final int POSITIONS_SHIFT = 8;
    public static final long POSITIONS_MASK = 0xFF_FFFF_FFFF_FFFFL;



    public WordMetadata() {
        this(emptyValue());
    }

    public WordMetadata(long value) {
        this(
                ((value >>> POSITIONS_SHIFT) & POSITIONS_MASK),
                (byte) (value & FLAGS_MASK)
        );
    }

    public WordMetadata(long positions,
                        Set<WordFlags> flags)
    {
        this(positions, encodeFlags(flags));
    }

    private static byte encodeFlags(Set<WordFlags> flags) {
        byte ret = 0;
        for (var flag : flags) { ret |= (byte) flag.asBit(); }
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

    public boolean hasFlag(WordFlags flag) {
        return (flags & flag.asBit()) != 0;
    }

    public String toString() {
        return "[positions=%s; %s]".formatted(BrailleBlockPunchCards.printBits(positions, 56), flagSet());
    }

    /* Encoded in a 64 bit long
     */
    public long encode() {
        long ret = 0;

        ret |= Byte.toUnsignedLong(flags);
        ret |= (positions & POSITIONS_MASK) << POSITIONS_SHIFT;

        return ret;
    }

    public boolean isEmpty() {
        return positions == 0 && flags == 0;
    }

    public static long emptyValue() {
        return 0L;
    }


    public EnumSet<WordFlags> flagSet() {
        return WordFlags.decode(flags);
    }

}
