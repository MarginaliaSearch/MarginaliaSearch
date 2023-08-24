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
    public void testRankBoundsAdd() {
        long encoded = UrlIdCodec.encodeId(0, 0);
        encoded = UrlIdCodec.addRank(1.f, encoded);
        assertEquals(0, UrlIdCodec.getDomainId(encoded));
        assertEquals(63, UrlIdCodec.getRank(encoded));
        assertEquals(0, UrlIdCodec.getDocumentOrdinal(encoded));
    }

    @Test
    public void testRemoveRank() {
        long encoded = UrlIdCodec.encodeId(0x7FFF_FFFF, ~0);
        encoded = UrlIdCodec.addRank(1.f, encoded);
        encoded = UrlIdCodec.removeRank(encoded);
        assertEquals(0x7FFF_FFFFL, UrlIdCodec.getDomainId(encoded));
        assertEquals(0, UrlIdCodec.getRank(encoded));
        assertEquals(0x03FF_FFFF, UrlIdCodec.getDocumentOrdinal(encoded));
    }

}