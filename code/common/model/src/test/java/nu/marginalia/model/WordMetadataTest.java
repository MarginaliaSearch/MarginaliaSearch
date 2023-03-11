package nu.marginalia.model;

import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordMetadataTest {

    @Test
    public void codecTest() {
        verifyCodec("Vanilla case", new WordMetadata(32, 0x7f0f0000,  EnumSet.allOf(WordFlags.class)));
        verifyCodec("Position high", new WordMetadata(32, 0xff0f0000,  EnumSet.allOf(WordFlags.class)));
        verifyCodec("No flags", new WordMetadata(32, 0xff0f0000, EnumSet.noneOf(WordFlags.class)));
        System.out.println(new WordMetadata(32, 0x7f0f0005, EnumSet.allOf(WordFlags.class)));
        System.out.println(new WordMetadata(32, 0xff0f0013,  EnumSet.noneOf(WordFlags.class)));
    }

    @Test
    public void testClampTfIdfLow() {
        var original = new WordMetadata(0x8000FFFF, 0, EnumSet.noneOf(WordFlags.class));
        var encoded = new WordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(0, encoded.tfIdf());
    }

    @Test
    public void testClampTfIdfHigh() {
        var original = new WordMetadata(0x7000FFFF, 0, EnumSet.noneOf(WordFlags.class));
        var encoded = new WordMetadata(original.encode());

        assertEquals(original.positions(), encoded.positions());
        assertEquals(65535, encoded.tfIdf());
    }

    public void verifyCodec(String message, WordMetadata data) {
        assertEquals(data, new WordMetadata(data.encode()), message);
    }


}