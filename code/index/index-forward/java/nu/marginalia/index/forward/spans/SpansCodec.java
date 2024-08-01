package nu.marginalia.index.forward.spans;

public class SpansCodec {
    public static long encode(long startOffset, long size) {
        assert size < 0x1000_0000L : "Size must be less than 2^28";

        return startOffset << 28 | (size & 0xFFF_FFFFL);
    }

    public static long decodeStartOffset(long encoded) {
        return encoded >>> 28;
    }

    public static long decodeSize(long encoded) {
        return encoded & 0x0FFF_FFFFL;
    }
}
