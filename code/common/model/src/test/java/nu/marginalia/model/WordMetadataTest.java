package nu.marginalia.model;

import nu.marginalia.model.crawl.EdgePageWordFlags;
import nu.marginalia.model.idx.WordMetadata;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordMetadataTest {

    @Test
    public void codecTest() {
        verifyCodec("Vanilla case", new WordMetadata(32, 0x7f0f0000, 1, EnumSet.allOf(EdgePageWordFlags.class)));
        verifyCodec("Position high", new WordMetadata(32, 0xff0f0000, 1, EnumSet.allOf(EdgePageWordFlags.class)));
        verifyCodec("No flags", new WordMetadata(32, 0xff0f0000, 1, EnumSet.noneOf(EdgePageWordFlags.class)));
        System.out.println(new WordMetadata(32, 0x7f0f0005, 1, EnumSet.allOf(EdgePageWordFlags.class)));
        System.out.println(new WordMetadata(32, 0xff0f0013, 1, EnumSet.noneOf(EdgePageWordFlags.class)));
    }

    @Test
    public void testClampTfIdfLow() {
        var original = new WordMetadata(0x8000FFFF, 0, 1, EnumSet.noneOf(EdgePageWordFlags.class));
        var encoded = new WordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(0, encoded.tfIdf());
    }

    @Test
    public void testClampTfIdfHigh() {
        var original = new WordMetadata(0x7000FFFF, 0, 1, EnumSet.noneOf(EdgePageWordFlags.class));
        var encoded = new WordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(65535, encoded.tfIdf());
    }

    @Test
    public void testClampCountLow() {
        var original = new WordMetadata(40, 0, -1, EnumSet.noneOf(EdgePageWordFlags.class));
        var encoded = new WordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(0, encoded.count());
    }

    @Test
    public void testClampCountHigh() {
        var original = new WordMetadata(40, 0, 17, EnumSet.noneOf(EdgePageWordFlags.class));
        var encoded = new WordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(15, encoded.count());
    }


    public void verifyCodec(String message, WordMetadata data) {
        assertEquals(data, new WordMetadata(data.encode()), message);
    }


}