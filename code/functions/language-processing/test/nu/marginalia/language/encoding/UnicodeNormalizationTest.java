package nu.marginalia.language.encoding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class UnicodeNormalizationTest {

    UnicodeNormalization unicodeNormalization = new UnicodeNormalization.FlattenAllLatin();

    @Test
    void flattenUnicodePlainAscii() {
        String s = "abc";

        // If the string is ascii, we don't want to allocate a copy

        assertSame(s, unicodeNormalization.flattenUnicode(s));
    }

    @Test
    void flattenUnicode() {
        String s = "Stülpnagelstraße";

        assertEquals("Stulpnagelstrasse", unicodeNormalization.flattenUnicode(s));
    }

    @Test
    void flattenUnicode2() {
        String s = "Koncevičius";

        assertEquals("Koncevicius", unicodeNormalization.flattenUnicode(s));
    }

    @Test
    void omitNonFlattenable() {
        String s = "[アグレッシブ烈子]";

        assertEquals("[]", unicodeNormalization.flattenUnicode(s));
    }
}