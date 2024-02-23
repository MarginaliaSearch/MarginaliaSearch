package nu.marginalia.model;


import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentMetadataTest {

    @Test
    public void codecYear() {
        var meta = new DocumentMetadata(0, 0, 0, 0, 192, 0, 0, (byte) 0);
        long encoded = meta.encode();
        var decoded = new DocumentMetadata(encoded);
        assertEquals(192, decoded.year());
    }

    @Test
    public void codecTopology() {
        var meta = new DocumentMetadata(0, 0, 0, 192, 0, 0, 0, (byte) 0);
        long encoded = meta.encode();
        var decoded = new DocumentMetadata(encoded);
        assertEquals(192, decoded.topology());
    }

    @Test
    public void codecSets() {
        var meta = new DocumentMetadata(0, 0, 0, 0, 0, 14, 0, (byte) 0);
        long encoded = meta.encode();
        var decoded = new DocumentMetadata(encoded);
        assertEquals(14, decoded.sets());
    }

    @Test
    public void codecQuality() {
        var meta = new DocumentMetadata(0, 0, 0, 0, 0, 0, 9, (byte) 0);
        long encoded = meta.encode();
        var decoded = new DocumentMetadata(encoded);
        assertEquals(9, decoded.quality());
    }

    @Test
    public void codecAvgSentLength() {

        for (int i = 0; i < 4; i++) {
            var meta = new DocumentMetadata(i, 0, 0, 0, 0, 0, 0, (byte) 0);
            assertEquals(i, meta.avgSentLength());
            long encoded = meta.encode();
            var decoded = new DocumentMetadata(encoded);
            assertEquals(i, decoded.avgSentLength());
        }

        var meta = new DocumentMetadata(5, 0, 0, 0, 0, 0, 0, (byte) 0);
        assertEquals(5, meta.avgSentLength());
        long encoded = meta.encode();
        var decoded = new DocumentMetadata(encoded);
        assertEquals(3, decoded.avgSentLength());
    }

    @Test
    public void codecFlags() {
        var meta = new DocumentMetadata(0, 0, 0, 0, 0, 0, 0, (byte) 255);
        long encoded = meta.encode();
        System.out.println(Long.toHexString(encoded));
        var decoded = new DocumentMetadata(encoded);
        System.out.println(decoded);
        assertEquals((byte) 255, decoded.flags());
    }

    @Test
    public void encRank() {
        var meta = new DocumentMetadata(0, 22, 8, EnumSet.noneOf(DocumentFlags.class))
                .withSizeAndTopology(0xffffffff, 5).encode();
        var enc2 = DocumentMetadata.encodeRank(meta, 83);

        assertEquals(83, DocumentMetadata.decodeRank(enc2));
        assertEquals(5, DocumentMetadata.decodeTopology(enc2));
    }

    @Test
    public void testYear() {
        for (int year = 1996; year < 2023; year++) {
            var meta = new DocumentMetadata(~0, new PubDate(null, year).yearByte(), ~0, EnumSet.allOf(DocumentFlags.class))
                    .withSizeAndTopology(~0, ~0);

            var encoded = DocumentMetadata.encodeRank(meta.encode(), 0);

            assertEquals(year, DocumentMetadata.decodeYear(encoded));
        }

        for (int year = 1996; year < 2023; year++) {
            var meta = new DocumentMetadata(0, new PubDate(null, year).yearByte(), 0, EnumSet.noneOf(DocumentFlags.class))
                    .withSizeAndTopology(0, 0);

            var encoded = DocumentMetadata.encodeRank(meta.encode(), 0);

            assertEquals(year, DocumentMetadata.decodeYear(encoded));
        }
    }
}