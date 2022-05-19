package nu.marginalia.wmsa.edge.crawler.fetcher;

import crawlercommons.mimetypes.MimeTypeDetector;
import nu.marginalia.wmsa.edge.model.crawl.EdgeContentType;
import org.jsoup.Jsoup;

import java.util.Arrays;
import java.util.Optional;

public class ContentTypeParser {

    static final MimeTypeDetector mimeTypeDetector = new MimeTypeDetector();

    public static EdgeContentType parse(String contentType, byte[] data) {
        return getContentTypeFromContentTypeString(contentType)
                .or(() -> getContentTypeStringFromTag(data))
                .orElseGet(() -> {
                    Optional<String> charset = getCharsetFromTag(data);
                    return new EdgeContentType(
                            Optional.ofNullable(contentType)
                                    .or(() -> Optional.ofNullable(mimeTypeDetector.detect(data)))
                                    .orElseGet(() -> ContentTypeParser.shittyMimeSniffer(data)), charset.orElse("ISO_8859_1"));
                });
    }

    private static Optional<EdgeContentType> getContentTypeFromContentTypeString(String contentType) {
        if (contentType != null && contentType.contains(";")) {
            var parts = contentType.split(";");
            var content = parts[0].trim();
            var extra = parts[1].trim();
            if (extra.startsWith("charset=")) {
                return Optional.of(new EdgeContentType(content, extra.substring("charset=".length())));
            }
        }
        return Optional.empty();
    }

    private static String shittyMimeSniffer(byte[] data) {

        for (int i = 0; i < data.length && i < 128; i++) {
            if (data[i] < 32) {
                return "application/binary";
            }
        }

        String startStr = new String(Arrays.copyOf(data, Math.min(128, data.length))).trim().toLowerCase();
        if (startStr.contains("<!doctype html") || startStr.contains("<html")) {
            return "text/html";
        }
        else {
            return "text/plain";
        }

    }

    private static Optional<EdgeContentType> getContentTypeStringFromTag(byte[] data) {
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
