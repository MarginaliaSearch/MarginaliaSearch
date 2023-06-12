package nu.marginalia.bigstring;

import nu.marginalia.bigstring.CompressedBigString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompressedBigStringTest {

    @Test
    public void testCompressDecompress() {
        String testString = "This is a test string that is longer than 64 characters. It should be compressed.";
        var bigString = new CompressedBigString(testString);
        assertEquals(testString, bigString.decode());
    }
}