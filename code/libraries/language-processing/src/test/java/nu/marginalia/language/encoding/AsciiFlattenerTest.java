package nu.marginalia.language.encoding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsciiFlattenerTest {

    @Test
    void flattenUnicodePlainAscii() {
        String s = "abc";

        // If the string is ascii, we don't want to allocate a copy

        assertSame(s, AsciiFlattener.flattenUnicode(s));
    }

    @Test
    void flattenUnicode() {
        String s = "Stülpnagelstraße";

        assertEquals("Stulpnagelstrasse", AsciiFlattener.flattenUnicode(s));
    }

    @Test
    void flattenUnicode2() {
        String s = "Koncevičius";

        assertEquals("Koncevicius", AsciiFlattener.flattenUnicode(s));
    }

    @Test
    void omitNonFlattenable() {
        String s = "[アグレッシブ烈子]";

        assertEquals("[]", AsciiFlattener.flattenUnicode(s));
    }
}