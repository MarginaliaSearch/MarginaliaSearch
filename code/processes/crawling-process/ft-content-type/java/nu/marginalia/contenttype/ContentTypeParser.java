package nu.marginalia.contenttype;

import crawlercommons.mimetypes.MimeTypeDetector;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;

import java.util.Arrays;
import java.util.Optional;

public class ContentTypeParser {

    static final MimeTypeDetector mimeTypeDetector = new MimeTypeDetector();

    /** Parse the content type and charset from a content type header and/or the body of a document,
     * best effort
     */
    public static ContentType parseContentType(
            @Nullable String contentTypeHeader,
            @NotNull byte[] body)
    {
        return getContentTypeFromContentTypeString(contentTypeHeader)
                .or(() -> getContentTypeStringFromTag(body))
                .orElseGet(() -> {
                    Optional<String> charset = getCharsetFromTag(body);
                    return new ContentType(
                            Optional.ofNullable(contentTypeHeader)
                                    .or(() -> Optional.ofNullable(mimeTypeDetector.detect(body)))
                                    .orElseGet(() -> ContentTypeParser.shittyMimeSniffer(body)), charset.orElse("ISO_8859_1"));
                });
    }

    /** Parse the charset from a content type string. */
    private static Optional<ContentType> getContentTypeFromContentTypeString(@Nullable String contentType) {
        if (contentType == null)
            return Optional.empty();

        var parts = StringUtils.split(contentType, ';');

        if (parts.length != 2) {
            return Optional.empty();
        }

        var content = parts[0].trim();
        var extra = parts[1].trim();

        if (!extra.startsWith("charset="))
            return Optional.empty();

        return Optional.of(new ContentType(content, extra.substring("charset=".length())));
    }

    private static String shittyMimeSniffer(byte[] data) {

        for (int i = 0; i < data.length && i < 128; i++) {
            if (data[i] < 32) {
                return "application/binary";
            }
        }

        String startStr = new String(Arrays.copyOf(data, Math.min(128, data.length))).trim().toLowerCase();
        if (startStr.contains("<!doctype html") || startStr.contains("<html")) {
            // note we use contains here, since xhtml may be served with a <?xml-style header first
            return "text/html";
        }
        else {
            return "text/plain";
        }

    }

    private static Optional<ContentType> getContentTypeStringFromTag(byte[] data) {
        String header = new String(Arrays.copyOf(data, Math.min(1024, data.length)));
        var doc = Jsoup.parse(header);
        for (var metaTag : doc.getElementsByTag("meta")) {
            if ("content-type".equalsIgnoreCase(metaTag.attr("http-equiv"))) {
                return getContentTypeFromContentTypeString(metaTag.attr("content"));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> getCharsetFromTag(byte[] data) {
        String header = new String(Arrays.copyOf(data, Math.min(1024, data.length)));
        var doc = Jsoup.parse(header);
        for (var metaTag : doc.getElementsByTag("meta")) {
            if (metaTag.hasAttr("charset")) {
                return Optional.of(metaTag.attr("charset"));
            }
        }
        return Optional.empty();
    }
}
