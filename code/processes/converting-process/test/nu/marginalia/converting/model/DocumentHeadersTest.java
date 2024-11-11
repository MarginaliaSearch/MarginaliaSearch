package nu.marginalia.converting.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class DocumentHeadersTest {

    @Test
    void testNull() {
        DocumentHeaders headers = new DocumentHeaders(null);
        Assertions.assertEquals("", headers.raw);
        Assertions.assertEquals(List.of(), headers.eachLine());
    }

    @Test
    void testEmpty() {
        DocumentHeaders headers = new DocumentHeaders("");
        Assertions.assertEquals("", headers.raw);
        Assertions.assertEquals(List.of(), headers.eachLine());
    }

    @Test
    void testDoubleNewlinesEmpty() {
        DocumentHeaders headers = new DocumentHeaders("server: test\r\n\n\r\nfoo: bar");
        Assertions.assertEquals(List.of("server: test", "foo: bar"), headers.eachLine());
    }

    @Test
    void containsIgnoreCaseGivenKeyAndValueInDifferentCasesReturnsTrue() {
        String raw = "Key1: Value1\r\nkey2: value2\r\nKEY3: VALUE3";
        DocumentHeaders headers = new DocumentHeaders(raw);

        Assertions.assertTrue(headers.containsIgnoreCase("key1", "value1"));
        Assertions.assertTrue(headers.containsIgnoreCase("key2", "value2"));
        Assertions.assertTrue(headers.containsIgnoreCase("key3", "value3"));
    }

}