package nu.marginalia.contenttype;

import java.nio.charset.*;

public class DocumentBodyToString {

    /** Get the string data from a document body, given the content type and charset */
    public static String getStringData(ContentType type, byte[] data) {
        Charset charset;
        try {
            if (type.charset() == null || type.charset().isBlank())
                charset = StandardCharsets.UTF_8;
            else {
                charset = Charset.forName(type.charset());
            }
        }
        catch (IllegalCharsetNameException ex) {
            // Fall back to UTF-8 if we don't understand what this is.  It's *probably* fine? Maybe?
            charset = StandardCharsets.UTF_8;
        }
        catch (UnsupportedCharsetException ex) {
            // This is usually like Macintosh Latin
            // (https://en.wikipedia.org/wiki/Macintosh_Latin_encoding)
            //
            // It's close enough to 8859-1 to serve
            charset = StandardCharsets.ISO_8859_1;
        }

        return new String(data, charset);
    }
}
