package nu.marginalia.crawl.retreival.fetcher.warc;

import okhttp3.Headers;
import org.netpreserve.jwarc.MessageHeaders;
import org.netpreserve.jwarc.WarcResponse;
import org.netpreserve.jwarc.WarcRevisit;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

public sealed interface HttpFetchResult {
    static ResultOk importWarc(WarcResponse response) throws IOException {
        var http = response.http();
        try (var body = http.body()) {
            byte[] bytes = body.stream().readAllBytes();

            return new ResultOk(
                    response.targetURI(),
                    http.status(),
                    http.headers(),
                    bytes,
                    0,
                    bytes.length
            );
        }
    }
    static ResultOk importWarc(WarcRevisit revisit) throws IOException {
        var http = revisit.http();
        try (var body = http.body()) {
            byte[] bytes = body.stream().readAllBytes();

            return new ResultOk(
                    revisit.targetURI(),
                    http.status(),
                    http.headers(),
                    bytes,
                    0,
                    bytes.length
            );
        }
    }
    record ResultOk(URI uri,
                    int statusCode,
                    Headers headers,
                    byte[] bytesRaw,
                    int bytesStart,
                    int bytesLength
    ) implements HttpFetchResult {

        public ResultOk(URI uri,
                        int statusCode,
                        MessageHeaders headers,
                        byte[] bytesRaw,
                        int bytesStart,
                        int bytesLength) {
            this(uri, statusCode, convertHeaders(headers), bytesRaw, bytesStart, bytesLength);
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

        public String header(String name) {
            return headers.get(name);
        }
        public List<String> allHeaders(String name) {
            return headers.values(name);
        }


    };
    record ResultError(Exception ex) implements HttpFetchResult { };
}
