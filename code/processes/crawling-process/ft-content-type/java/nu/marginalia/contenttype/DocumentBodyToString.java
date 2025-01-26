package nu.marginalia.contenttype;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

    public static Document getParsedData(ContentType type, byte[] data, int maxLength, String url) throws IOException {
        final Charset charset;

        if (type.charset() == null || type.charset().isBlank()) {
            charset = StandardCharsets.UTF_8;
        } else {
            charset = charsetMap.computeIfAbsent(type, DocumentBodyToString::computeCharset);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(data, 0, Math.min(data.length, maxLength));

        return Jsoup.parse(bais, charset.name(), url);
    }

    private static Charset computeCharset(ContentType type) {
        if (type.charset() == null || type.charset().isBlank())
            return StandardCharsets.UTF_8;
        else {
            return type.asCharset();
        }
    }
}
