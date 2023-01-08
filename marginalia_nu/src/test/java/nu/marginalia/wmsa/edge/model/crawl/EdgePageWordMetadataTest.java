package nu.marginalia.wmsa.edge.model.crawl;

import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordMetadata;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EdgePageWordMetadataTest {

    @Test
    public void codecTest() {
        verifyCodec("Vanilla case", new EdgePageWordMetadata(32, 0x7f0f0000, 1, EnumSet.allOf(EdgePageWordFlags.class)));
        verifyCodec("Position high", new EdgePageWordMetadata(32, 0xff0f0000, 1, EnumSet.allOf(EdgePageWordFlags.class)));
        verifyCodec("No flags", new EdgePageWordMetadata(32, 0xff0f0000, 1, EnumSet.noneOf(EdgePageWordFlags.class)));
        System.out.println(new EdgePageWordMetadata(32, 0x7f0f0005, 1, EnumSet.allOf(EdgePageWordFlags.class)));
        System.out.println(new EdgePageWordMetadata(32, 0xff0f0013, 1, EnumSet.noneOf(EdgePageWordFlags.class)));
    }

    @Test
    public void testClampTfIdfLow() {
        var original = new EdgePageWordMetadata(0x8000FFFF, 0, 1, EnumSet.noneOf(EdgePageWordFlags.class));
        var encoded = new EdgePageWordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(0, encoded.tfIdf());
    }

    @Test
    public void testClampTfIdfHigh() {
        var original = new EdgePageWordMetadata(0x7000FFFF, 0, 1, EnumSet.noneOf(EdgePageWordFlags.class));
        var encoded = new EdgePageWordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(65535, encoded.tfIdf());
    }

    @Test
    public void testClampCountLow() {
        var original = new EdgePageWordMetadata(40, 0, -1, EnumSet.noneOf(EdgePageWordFlags.class));
        var encoded = new EdgePageWordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(0, encoded.count());
    }

    @Test
    public void testClampCountHigh() {
        var original = new EdgePageWordMetadata(40, 0, 17, EnumSet.noneOf(EdgePageWordFlags.class));
        var encoded = new EdgePageWordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(15, encoded.count());
    }


    public void verifyCodec(String message, EdgePageWordMetadata data) {
        assertEquals(data, new EdgePageWordMetadata(data.encode()), message);
    }


}