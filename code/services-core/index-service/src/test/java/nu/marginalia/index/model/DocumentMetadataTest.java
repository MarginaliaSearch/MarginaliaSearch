package nu.marginalia.index.model;


import nu.marginalia.model.crawl.EdgePageDocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentMetadataTest {

    @Test
    public void codecYear() {
        var meta = new DocumentMetadata(0, 0, 0, 192, 0, 0, (byte) 0);
        long encoded = meta.encode();
        var decoded = new DocumentMetadata(encoded);
        assertEquals(192, decoded.year());
    }

    @Test
    public void codecTopology() {
        var meta = new DocumentMetadata(0, 0, 192, 0, 0, 0, (byte) 0);
        long encoded = meta.encode();
        var decoded = new DocumentMetadata(encoded);
        assertEquals(192, decoded.topology());
    }

    @Test
    public void codecSets() {
        var meta = new DocumentMetadata(0, 0, 0, 0, 14, 0, (byte) 0);
        long encoded = meta.encode();
        var decoded = new DocumentMetadata(encoded);
        assertEquals(14, decoded.sets());
    }

    @Test
    public void codecQuality() {
        var meta = new DocumentMetadata(0, 0, 0, 0, 0, 9, (byte) 0);
        long encoded = meta.encode();
        var decoded = new DocumentMetadata(encoded);
        assertEquals(9, decoded.quality());
    }

    @Test
    public void codecFlags() {
        var meta = new DocumentMetadata(0, 0, 0, 0, 0, 0, (byte) 255);
        long encoded = meta.encode();
        System.out.println(Long.toHexString(encoded));
        var decoded = new DocumentMetadata(encoded);
        System.out.println(decoded);
        assertEquals((byte) 255, decoded.flags());
    }

    @Test
    public void encSize() {
        assertEquals(100, new DocumentMetadata(0).withSize(145).size());
        assertEquals(100, DocumentMetadata.decodeSize(new DocumentMetadata(0).withSize(145).encode()));

        assertEquals(50, new DocumentMetadata(0).withSize(4).size());
        assertEquals(50, DocumentMetadata.decodeSize(new DocumentMetadata(0).withSize(4).encode()));

        assertEquals(50 * 255, DocumentMetadata.decodeSize(new DocumentMetadata(0).withSize(Integer.MAX_VALUE).encode()));
        assertEquals(50 * 255, new DocumentMetadata(0).withSize(Integer.MAX_VALUE).size());
    }

    @Test
    public void encRank() {
        var meta = new DocumentMetadata(5, 22, 3, 8, EnumSet.noneOf(EdgePageDocumentFlags.class))
                .withSize(0xffffffff).encode();
        var enc2 = DocumentMetadata.encodeRank(meta, 83);

        assertEquals(83, DocumentMetadata.decodeRank(enc2));
        assertEquals(5, DocumentMetadata.decodeTopology(enc2));
    }
}