package nu.marginalia.model.idx;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class WordFlagsTest {

    @Test
    void testBits() {
        int seen = 0;

        for (WordFlags flag : WordFlags.values()) {
            int bit = Byte.toUnsignedInt(flag.asBit());

            assertEquals(1, Integer.bitCount(bit), flag + " must encode to exactly one bit");
            assertEquals(0, seen & bit, flag + " must not share a bit with another flag");

            seen |= bit;
        }
    }

    @Test
    void testEncoding() {
        for (WordFlags flag : WordFlags.values()) {
            byte encoded = WordFlags.encode(EnumSet.of(flag));

            assertTrue(flag.isPresent(encoded), flag + " must be present in its own encoding");
            assertFalse(flag.isAbsent(encoded), flag + " must not be absent from its own encoding");

            for (WordFlags other : WordFlags.values()) {
                if (other != flag) {
                    assertFalse(other.isPresent(encoded), other + " must not be present in the encoding of " + flag);
                    assertTrue(other.isAbsent(encoded), other + " must be absent from the encoding of " + flag);
                }
            }
        }
    }

    @Test
    void encodeDecodeRoundTrip() {
        assertEquals(EnumSet.noneOf(WordFlags.class), WordFlags.decode(WordFlags.encode(EnumSet.noneOf(WordFlags.class))));
        assertEquals(EnumSet.allOf(WordFlags.class), WordFlags.decode(WordFlags.encode(EnumSet.allOf(WordFlags.class))));

        for (WordFlags flag : WordFlags.values()) {
            EnumSet<WordFlags> single = EnumSet.of(flag);
            assertEquals(single, WordFlags.decode(WordFlags.encode(single)));
        }
    }
}
