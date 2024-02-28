package nu.marginalia.crawling.body;

import nu.marginalia.contenttype.ContentType;
import okhttp3.Headers;
import org.jsoup.Jsoup;
import org.netpreserve.jwarc.MessageHeaders;
import org.netpreserve.jwarc.WarcResponse;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;

/* FIXME:  This interface has a very unfortunate name that is not very descriptive.
 */
public sealed interface HttpFetchResult {

    boolean isOk();

    /** Convert a WarcResponse to a HttpFetchResult */
    static HttpFetchResult importWarc(WarcResponse response) {
        try {
            var http = response.http();

            try (var body = http.body()) {
                byte[] bytes = body.stream().readAllBytes();

                String ipAddress = response
                        .ipAddress()
                        .map(InetAddress::getHostAddress)
                        .orElse("");

                return new ResultOk(
                        response.targetURI(),
                        http.status(),
                        http.headers(),
                        ipAddress,
                        bytes,
                        0,
                        bytes.length
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
                    Headers headers,
                    String ipAddress,
                    byte[] bytesRaw,
                    int bytesStart,
                    int bytesLength
    ) implements HttpFetchResult {

        public boolean isOk() {
            return statusCode >= 200 && statusCode < 300;
        }

        public ResultOk(URI uri,
                        int statusCode,
                        MessageHeaders headers,
                        String ipAddress,
                        byte[] bytesRaw,
                        int bytesStart,
                        int bytesLength) {
            this(uri, statusCode, convertHeaders(headers), ipAddress, bytesRaw, bytesStart, bytesLength);
        }

        private static Headers convertHeaders(MessageHeaders headers) {
            var ret = new Headers.Builder();
            for (var header : headers.map().entrySet()) {
                for (var value : header.getValue()) {
                    ret.add(header.getKey(), value);
                }
            }
            return ret.build();
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytesRaw, bytesStart, bytesLength);
        }

        public Optional<Document> parseDocument() throws IOException {
            return DocumentBodyExtractor.asString(this).flatMapOpt((contentType, body) -> {
                if (contentType.is("text/html")) {
                    return Optional.of(Jsoup.parse(body));
                }
                else {
                    return Optional.empty();
                }
            });
        }

        public String header(String name) {
            return headers.get(name);
        }

    }

    /** This is a special case where the document was not fetched
     * because it was already in the database.  In this case, we
     * replace the original data.
     *
     * @see Result304Raw for the case where the document has not yet been replaced with the reference data.
     */
    record Result304ReplacedWithReference(String url, ContentType contentType, String body) implements HttpFetchResult {

        public boolean isOk() {
            return true;
        }

        public Optional<Document> parseDocument() {
            try {
                return Optional.of(Jsoup.parse(body));
            }
            catch (Exception ex) {
                return Optional.empty();
            }
        }
    }

    /** Fetching resulted in an exception */
    record ResultException(Exception ex) implements HttpFetchResult {
        public boolean isOk() {
            return false;
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
