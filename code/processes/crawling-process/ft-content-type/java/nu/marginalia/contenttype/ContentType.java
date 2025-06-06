package nu.marginalia.contenttype;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;

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

        if (charset.toLowerCase().startsWith("charset=")) {
            charset = charset.substring("charset=".length());
        }

        return new ContentType(contentType, charset);
    }

    /** Best effort method for turning the provided charset string into a Java charset method,
     * with some guesswork-heuristics for when it doesn't work
     */
    public Charset asCharset() {
        try {
            if (Charset.isSupported(charset)) {
                return Charset.forName(charset);
            } else if (charset.equalsIgnoreCase("macintosh-latin")) {
                return StandardCharsets.ISO_8859_1;
            } else {
                return StandardCharsets.UTF_8;
            }
        }
        catch (IllegalCharsetNameException ex) { // thrown by Charset.isSupported()
            return StandardCharsets.UTF_8;
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
