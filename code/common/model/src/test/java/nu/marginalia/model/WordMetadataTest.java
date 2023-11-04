package nu.marginalia.model;

import nu.marginalia.bbpc.BrailleBlockPunchCards;
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
        verifyCodec("No flags, some bits", new WordMetadata(0x3f_7f7f_7f7f_7f7fL, EnumSet.noneOf(WordFlags.class)));
        verifyCodec("No flags, all bits", new WordMetadata( 0x3f_ffff_ffff_ffffL, EnumSet.noneOf(WordFlags.class)));
        verifyCodec("All flags, all bits", new WordMetadata( 0x3f_ffff_ffff_ffffL, EnumSet.allOf(WordFlags.class)));
        System.out.println(new WordMetadata(0x7f0f0005L, EnumSet.allOf(WordFlags.class)));
        System.out.println(new WordMetadata(0xff0f0013L,  EnumSet.noneOf(WordFlags.class)));
        System.out.println(new WordMetadata(0xf0f000ff0f0013L,  EnumSet.allOf(WordFlags.class)));
        System.out.println(new WordMetadata(0xf0f000ff0f0013L,  (byte)-1));
        System.out.println(new WordMetadata(0x3f_ffff_ffff_ffffL,  (byte)0));
        System.out.println(new WordMetadata(0x3f_ffff_ffff_ffffL,  (byte)0));
        System.out.println(BrailleBlockPunchCards.printBits(new WordMetadata(~0L, (byte) 0).encode(), 64));
        System.out.println(BrailleBlockPunchCards.printBits(new WordMetadata(0, (byte) 0xff).encode(), 64));
        System.out.println(BrailleBlockPunchCards.printBits(131973L, 64));
        System.out.println(new WordMetadata(131973L));
    }

    public void verifyCodec(String message, WordMetadata data) {
        System.out.println(BrailleBlockPunchCards.printBits(data.encode(), 64));
        assertEquals(data, new WordMetadata(data.encode()), message);
    }


}