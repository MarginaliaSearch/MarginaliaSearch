package nu.marginalia.contenttype;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ContentTypeParserTest {

     @Test
     public void testParseContentTypeWithHeader() {
         byte[] body = "<!DOCTYPE html><html><head><title>Title</title></head><body></body></html>".getBytes(StandardCharsets.UTF_8);
         String contentTypeHeader = "text/html; charset=UTF-8";
         ContentType result = ContentTypeParser.parseContentType(contentTypeHeader, body);
         assertNotNull(result);
         assertEquals("text/html", result.contentType());
         assertEquals("UTF-8", result.charset());
     }

     @Test
     public void testParseContentTypeWithMetaCharset() {
         byte[] body = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Title</title></head><body></body></html>".getBytes(StandardCharsets.UTF_8);
         ContentType result = ContentTypeParser.parseContentType(null, body);
         assertNotNull(result);
         assertEquals("text/html", result.contentType());
         assertEquals("UTF-8", result.charset());
     }

     @Test
     public void testParseContentTypeWithHeaderValueAbsent() {
         byte[] body = "Some random text.".getBytes(StandardCharsets.UTF_8);
         String contentTypeHeader = "text/plain";
         ContentType result = ContentTypeParser.parseContentType(contentTypeHeader, body);
         assertNotNull(result);
         assertEquals("text/plain", result.contentType());
         assertEquals("ISO_8859_1", result.charset());
     }

     @Test
     public void testParseContentTypeWithBinaryData() {
         byte[] body = new byte[128];
         body[0] = 31; // ascii value less than 32
         ContentType result = ContentTypeParser.parseContentType(null, body);
         assertNotNull(result);
         assertEquals("application/binary", result.contentType());
         assertEquals("ISO_8859_1", result.charset());
     }
}