package nu.marginalia.index.positions;

/** A utility class for encoding and decoding position data offsets,
 * the data is encoded by using the highest 16 bits to store the offset,
 * and the remaining 48 bits to store the size of the data.
 * <p></p>
 * This lets us address 256 TB of data, with up to 64 KB of position data for each term,
 * which is ample headroom for both the size of the data and the number of positions.
 * */
public class PositionCodec {

    public static long encode(int length, long offset) {
        assert decodeSize(offset) == 0 : "Offset must be less than 2^48";

        return (long) length << 48 | offset;
    }

    public static int decodeSize(long sizeEncodedOffset) {
        return (int) ((sizeEncodedOffset & 0xFFFF_0000_0000_0000L) >>> 48);
    }
    public static long decodeOffset(long sizeEncodedOffset) {
        return sizeEncodedOffset & 0x0000_FFFF_FFFF_FFFFL;
    }

}
