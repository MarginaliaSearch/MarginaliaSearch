package nu.marginalia.model.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlIdCodecTest {
    @Test
    public void testDocumentBounds() {
        long encoded = UrlIdCodec.encodeId(0, ~0);
        assertEquals(0, UrlIdCodec.getDomainId(encoded));
    }

    @Test
    public void testDomainBounds() {
        long encoded = UrlIdCodec.encodeId(~0, 0);
        assertEquals(0x7FFF_FFFF, UrlIdCodec.getDomainId(encoded));
        assertEquals(0, UrlIdCodec.getRank(encoded));
        assertEquals(0, UrlIdCodec.getDocumentOrdinal(encoded));
    }

    @Test
    public void testRankBounds() {
        long encoded = UrlIdCodec.encodeIdWithRank(1.0f, 0, 0);
        assertEquals(0, UrlIdCodec.getDomainId(encoded));
        assertEquals(63, UrlIdCodec.getRank(encoded));
        assertEquals(0, UrlIdCodec.getDocumentOrdinal(encoded));
    }

    @Test
    public void testRankBoundsNeg() {
        long encoded = UrlIdCodec.encodeIdWithRank(-1.0f, 0, 0);
        assertEquals(0, UrlIdCodec.getDomainId(encoded));
        assertEquals(0, UrlIdCodec.getRank(encoded));
        assertEquals(0, UrlIdCodec.getDocumentOrdinal(encoded));
    }
}