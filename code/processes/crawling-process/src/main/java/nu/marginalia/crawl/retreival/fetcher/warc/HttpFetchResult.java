package nu.marginalia.crawl.retreival.fetcher.warc;

import okhttp3.Headers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

public sealed interface HttpFetchResult {
    record ResultOk(URI uri,
                    int statusCode,
                    Headers headers,
                    byte[] bytesRaw,
                    int bytesStart,
                    int bytesLength
    ) implements HttpFetchResult {
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
