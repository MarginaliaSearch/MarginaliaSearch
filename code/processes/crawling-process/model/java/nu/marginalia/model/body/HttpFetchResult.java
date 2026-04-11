package nu.marginalia.model.body;

import nu.marginalia.contenttype.ContentType;
import nu.marginalia.model.EdgeUrl;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.netpreserve.jwarc.WarcResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

/* FIXME:  This interface has a very unfortunate name that is not very descriptive.
 */
public sealed interface HttpFetchResult {

    boolean isOk();

    final int MAX_BODY_SIZE = Integer.getInteger("crawler.maxFetchSize", 32 * 1024 * 1024);

    /** Convert a WarcResponse to a HttpFetchResult */
    static HttpFetchResult importWarc(WarcResponse response) {
        try {
            var http = response.http();

            try (var body = http.body()) {
                String ipAddress = response
                        .ipAddress()
                        .map(InetAddress::getHostAddress)
                        .orElse("");

                Header[] headers = http.headers().map().entrySet().stream()
                        .mapMulti((entry, c) -> {
                            String key = entry.getKey();
                            if (key.isBlank()) return;
                            if (!Character.isAlphabetic(key.charAt(0))) return;

                            for (var val : entry.getValue()) {
                                c.accept(new BasicHeader(key, val));
                            }
                        })
                        .toArray(Header[]::new);

                return ResultOk.forStreamedBytes(
                        response.targetURI(),
                        http.status(),
                        headers,
                        ipAddress,
                        body.stream()
                );
            }
        }
        catch (Exception ex) {
            return new ResultException(ex);
        }
    }


    /** Corresponds to a successful retrieval of a document
     * from the remote server.  Note that byte[] is only borrowed
     * and subsequent calls may overwrite the contents of this buffer.
     */
    record ResultOk(URI uri,
                    int statusCode,
                    Header[] headers,
                    String ipAddress,
                    byte[] bytes
    ) implements HttpFetchResult {

        public static ResultOk forStreamedBytes(URI uri,
                                                int statusCode,
                                                Header[] headers,
                                                String ipAddress,
                                                InputStream stream) throws IOException {
            boolean isGzip = false;

            for (var header : headers) {
                if ("Content-Encoding".equalsIgnoreCase(header.getName())) {
                    isGzip = "gzip".equalsIgnoreCase(header.getValue());

                    // Reconstruct a clean header array that doesn't claim the data is compressed
                    headers = Arrays.stream(headers)
                            .filter(h -> h != header)
                            .toArray(Header[]::new);

                    break;
                }
            }

            if (isGzip) stream = new GZIPInputStream(stream);
            byte[] bytes = stream.readNBytes(MAX_BODY_SIZE);

            return new ResultOk(uri, statusCode, headers, ipAddress, bytes);
        }

        public boolean isOk() {
            return statusCode >= 200 && statusCode < 300;
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        public byte[] getBodyBytes() {
            return bytes;
        }

        public Optional<Document> parseDocument() {
            return DocumentBodyExtractor.asString(this).flatMapOpt((contentType, body) -> {
                if (contentType.is("text/html")) {
                    return Optional.of(Jsoup.parse(body));
                }
                else {
                    return Optional.empty();
                }
            });
        }

        @Nullable
        public String header(String name) {
            for (var header : headers) {
                if (header.getName().equalsIgnoreCase(name)) {
                    String headerValue = header.getValue();
                    return headerValue;
                }
            }
            return null;
        }

        public int byteLength() {
            return bytes.length;
        }
    }

    /** This is a special case where the document was not fetched
     * because it was already in the database.  In this case, we
     * replace the original data.
     *
     * @see Result304Raw for the case where the document has not yet been replaced with the reference data.
     */
    record Result304ReplacedWithReference(String url, ContentType contentType, byte[] body) implements HttpFetchResult {
        public boolean isOk() {
            return true;
        }
    }

    /** Fetching resulted in an exception */
    record ResultException(Exception ex) implements HttpFetchResult {
        public boolean isOk() {
            return false;
        }
    }

    record ResultRedirect(EdgeUrl url) implements HttpFetchResult {
        public boolean isOk() {
            return true;
        }
    }

    /** Fetching resulted in a HTTP 304, the remote content is identical to
     * our reference copy.  This will be replaced with a Result304ReplacedWithReference
     * at a later stage.
     *
     * @see Result304ReplacedWithReference
     */
    record Result304Raw() implements HttpFetchResult {
        public boolean isOk() {
            return false;
        }
    }

    /** No result.  This is typically injected at a later stage
     * of processing, e.g. after filtering out irrelevant responses.
     */
    record ResultNone() implements HttpFetchResult {
        public boolean isOk() {
            return false;
        }
    }
}
