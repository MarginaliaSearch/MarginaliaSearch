package nu.marginalia.model;

import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordMetadataTest {

    @Test
    public void codecTest() {
        verifyCodec("Vanilla case", new WordMetadata(0x7f0f0000L,  EnumSet.allOf(WordFlags.class)));
        verifyCodec("Position 32bit", new WordMetadata(0xff0f0000L,  EnumSet.allOf(WordFlags.class)));
        verifyCodec("Position all", new WordMetadata(0xffff_ff0f_0000L,  EnumSet.allOf(WordFlags.class)));
        verifyCodec("No flags", new WordMetadata( 0xff0f0000L, EnumSet.noneOf(WordFlags.class)));
        System.out.println(new WordMetadata(0x7f0f0005L, EnumSet.allOf(WordFlags.class)));
        System.out.println(new WordMetadata(0xff0f0013L,  EnumSet.noneOf(WordFlags.class)));
        System.out.println(new WordMetadata(0xf0f000ff0f0013L,  EnumSet.allOf(WordFlags.class)));
    }

    public void verifyCodec(String message, WordMetadata data) {
        assertEquals(data, new WordMetadata(data.encode()), message);
    }


}