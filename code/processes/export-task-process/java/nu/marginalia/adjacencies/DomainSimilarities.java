package nu.marginalia.adjacencies;

import it.unimi.dsi.fastutil.longs.LongList;

public record DomainSimilarities(int domainId, LongList encodedSimilarities) {

    public static long encode(int otherId, float similarity) {
        return  ((long) Float.floatToRawIntBits(similarity) << 32) | (otherId & 0xFFFF_FFFFL);
    }

    public static float deocdeSimilarity(long encodedId) {
        return Float.intBitsToFloat((int) (encodedId >>> 32));
    }

    public static int decodeOtherId(long encodedId) {
        return (int) (encodedId & 0xFFFF_FFFFL);
    }
}
