package nu.marginalia.wmsa.edge.index.model;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EdgePageDocumentsMetadataTest {

    @Test
    public void codecYear() {
        var meta = new EdgePageDocumentsMetadata(0, 0, 192, 0, 0, (byte) 0);
        long encoded = meta.encode();
        var decoded = new EdgePageDocumentsMetadata(encoded);
        assertEquals(192, decoded.year());
    }

    @Test
    public void codecTopology() {
        var meta = new EdgePageDocumentsMetadata(0, 192, 0, 0, 0, (byte) 0);
        long encoded = meta.encode();
        var decoded = new EdgePageDocumentsMetadata(encoded);
        assertEquals(192, decoded.topology());
    }

    @Test
    public void codecSets() {
        var meta = new EdgePageDocumentsMetadata(0, 0, 0, 14, 0, (byte) 0);
        long encoded = meta.encode();
        var decoded = new EdgePageDocumentsMetadata(encoded);
        assertEquals(14, decoded.sets());
    }

    @Test
    public void codecQuality() {
        var meta = new EdgePageDocumentsMetadata(0, 0, 0, 0, 9, (byte) 0);
        long encoded = meta.encode();
        var decoded = new EdgePageDocumentsMetadata(encoded);
        assertEquals(9, decoded.quality());
    }

    @Test
    public void codecFlags() {
        var meta = new EdgePageDocumentsMetadata(0, 0, 0, 0, 0, (byte) 255);
        long encoded = meta.encode();
        System.out.println(Long.toHexString(encoded));
        var decoded = new EdgePageDocumentsMetadata(encoded);
        System.out.println(decoded);
        assertEquals((byte) 255, decoded.flags());
    }

    @Test
    public void encSize() {
        assertEquals(100, new EdgePageDocumentsMetadata(0).withSize(145).size());
        assertEquals(100, EdgePageDocumentsMetadata.decodeSize(new EdgePageDocumentsMetadata(0).withSize(145).encode()));

        assertEquals(50, new EdgePageDocumentsMetadata(0).withSize(4).size());
        assertEquals(50, EdgePageDocumentsMetadata.decodeSize(new EdgePageDocumentsMetadata(0).withSize(4).encode()));

        assertEquals(50*255, EdgePageDocumentsMetadata.decodeSize(new EdgePageDocumentsMetadata(0).withSize(Integer.MAX_VALUE).encode()));
        assertEquals(50*255, new EdgePageDocumentsMetadata(0).withSize(Integer.MAX_VALUE).size());
    }
}