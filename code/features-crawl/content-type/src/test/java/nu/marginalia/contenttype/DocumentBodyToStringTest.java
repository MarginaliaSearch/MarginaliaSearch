package nu.marginalia.contenttype;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

public class DocumentBodyToStringTest {
    @Test
    public void testGetStringData_onUTF8(){

        ContentType type = new ContentType("text/html", "UTF-8");

        String expected = "Hello, World!";
        byte[] data = expected.getBytes(StandardCharsets.UTF_8);

        String result = DocumentBodyToString.getStringData(type, data);

        assertEquals(expected, result, "Result should match the expected string");
    }

    @Test
    public void testGetStringData_onIllegalCharsetName(){

        ContentType type = new ContentType("text/html", "unsupportedname");

        String expected = "Hello, World!";
        byte[] data = expected.getBytes(StandardCharsets.UTF_8);

        String result = DocumentBodyToString.getStringData(type, data);

        assertEquals(expected, result, "Result should match the expected string if charset is illegal name");
    }

    @Test
    public void testGetStringData_onUnsupportedCharset(){

        ContentType type = new ContentType("text/html", "Macintosh");

        String expected = "Hello, World!";
        byte[] data = expected.getBytes(StandardCharsets.UTF_8);

        String result = DocumentBodyToString.getStringData(type, data);

        assertEquals(expected, result, "Result should fall back to UTF-8 parsing if charset is unsupported");
    }

}