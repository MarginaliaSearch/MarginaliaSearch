package nu.marginalia.contenttype;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/** Content type and charset of a document
 * @param contentType The content type, e.g. "text/html"
 * @param charset The charset, e.g. "UTF-8"
 */
public record ContentType(String contentType, String charset) {
    public static ContentType parse(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank())
            return new ContentType(null,  null);

        String[] parts = StringUtils.split(contentTypeHeader, ";", 2);
        String contentType = parts[0].trim();
        String charset = parts.length > 1 ? parts[1].trim() : "UTF-8";

        return new ContentType(contentType, charset);
    }

    public Charset asCharset() {
        try {
            int eqAt = charset.indexOf('=');
            String charset = this.charset;
            if (eqAt >= 0) {
                charset = charset.substring(eqAt + 1);
            }
            if (Charset.isSupported(charset)) {
                return Charset.forName(charset);
            } else {
                return StandardCharsets.UTF_8;
            }
        }
        catch (IllegalCharsetNameException ex) {
            // Fall back to UTF-8 if we don't understand what this is.  It's *probably* fine? Maybe?
            return StandardCharsets.UTF_8;
        }
        catch (UnsupportedCharsetException ex) {
            // This is usually like Macintosh Latin
            // (https://en.wikipedia.org/wiki/Macintosh_Latin_encoding)
            //
            // It's close enough to 8859-1 to serve
            return StandardCharsets.ISO_8859_1;
        }
    }

    public boolean is(String contentType) {
        return this.contentType.equalsIgnoreCase(contentType);
    }

    public String toString() {
        if (charset == null || charset.isBlank())
            return contentType;

        return contentType + "; charset=" + charset;
    }
}
