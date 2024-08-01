package nu.marginalia.contenttype;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DocumentBodyToString {
    private static final Map<ContentType, Charset> charsetMap = new ConcurrentHashMap<>();

    /** Get the string data from a document body, given the content type and charset */
    public static String getStringData(ContentType type, byte[] data) {
        final Charset charset;

        if (type.charset() == null || type.charset().isBlank()) {
            charset = StandardCharsets.UTF_8;
        } else {
            charset = charsetMap.computeIfAbsent(type, DocumentBodyToString::computeCharset);
        }

        return new String(data, charset);
    }

    private static Charset computeCharset(ContentType type) {
        try {
            if (type.charset() == null || type.charset().isBlank())
                return StandardCharsets.UTF_8;
            else {
                return Charset.forName(type.charset());
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
}
