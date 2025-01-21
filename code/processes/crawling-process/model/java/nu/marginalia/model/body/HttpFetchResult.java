package nu.marginalia.model.body;

import nu.marginalia.contenttype.ContentType;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.netpreserve.jwarc.MessageHeaders;
import org.netpreserve.jwarc.WarcResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpHeaders;
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
                    HttpHeaders headers,
                    String ipAddress,
                    byte[] bytesRaw,
                    int bytesStart,
                    int bytesLength
    ) implements HttpFetchResult {

        public ResultOk(URI uri, int status, MessageHeaders headers, String ipAddress, byte[] bytes, int bytesStart, int length) {
            this(uri, status, HttpHeaders.of(headers.map(), (k,v) -> true), ipAddress, bytes, bytesStart, length);
        }

        public boolean isOk() {
            return statusCode >= 200 && statusCode < 300;
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytesRaw, bytesStart, bytesLength);
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
            return headers.firstValue(name).orElse(null);
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
