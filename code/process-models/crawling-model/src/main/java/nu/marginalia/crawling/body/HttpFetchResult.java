package nu.marginalia.crawling.body;

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
import java.util.List;
import java.util.Optional;

public sealed interface HttpFetchResult {

    boolean isOk();

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
            return switch(DocumentBodyExtractor.asString(this))  {
                case DocumentBodyResult.Ok<String> ok when "text/html".equalsIgnoreCase(ok.contentType())
                        -> Optional.of(Jsoup.parse(ok.body()));
                default -> Optional.empty();
            };
        }

        public String header(String name) {
            return headers.get(name);
        }
        public List<String> allHeaders(String name) {
            return headers.values(name);
        }


    };
    record ResultRetained(String url, String contentType, String body) implements HttpFetchResult {

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
    };
    record ResultException(Exception ex) implements HttpFetchResult {
        public boolean isOk() {
            return false;
        }
    };
    record ResultSame() implements HttpFetchResult {
        public boolean isOk() {
            return false;
        }
    };
    record ResultNone() implements HttpFetchResult {
        public boolean isOk() {
            return false;
        }
    };
}
