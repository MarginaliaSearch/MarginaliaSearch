package nu.marginalia.ping.fetcher;

import com.google.inject.Inject;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.ping.fetcher.response.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PingHttpFetcher {
    private final UserAgent userAgent = WmsaHome.getUserAgent();
    private final HttpClient client;

    @Inject
    public PingHttpFetcher(HttpClient client) {
        this.client = client;
    }

    public PingRequestResponse fetchUrl(String url, Method method, String etag, String lastModified) {


        HttpGet request = new HttpGet(URI.create(url));

        request.addHeader("User-Agent", userAgent.uaString());
        request.addHeader("Accept-Encoding", "gzip");
        request.addHeader("Accept-Language", "en,*;q=0.5");
        request.addHeader("Accept", "text/html, application/xhtml+xml, text/*;q=0.8");

        if (etag != null) {
            request.addHeader("If-None-Match", etag);
        }
        if (lastModified != null) {
            request.addHeader("If-Modified-Since", lastModified);
        }

        HttpClientContext context = HttpClientContext.create();
        try {
            Instant start = Instant.now();
            return client.execute(request, context, (rsp) -> {

                var entity = rsp.getEntity();

                try {
                    Header[] rawHeaders = rsp.getHeaders();
                    Map<String, List<String>> headers = new HashMap<>(rawHeaders.length);
                    for (Header header : rawHeaders) {
                        headers.computeIfAbsent(header.getName(), k -> new ArrayList<>())
                                .add(header.getValue());
                    }

                    if (method == Method.GET && entity == null) {
                        return new ProtocolError("GET request returned no content");
                    }


                    consumeData(entity.getContent(), request, Duration.of(15, ChronoUnit.SECONDS));

                    byte[] body = entity != null ? EntityUtils.toByteArray(entity) : null;

                    Duration responseTime = Duration.between(start, Instant.now());

                    return PingRequestResponse.of(
                            rsp.getVersion(),
                            rsp.getCode(),
                            headers,
                            responseTime,
                            context.getSSLSession()
                    );
                } finally {
                    EntityUtils.consume(entity);
                }
            });
        } catch (SocketTimeoutException ex) {
            return new TimeoutResponse(ex.getMessage());
        } catch (HttpHostConnectException | SSLHandshakeException e) {
            return new ConnectionError(e.getClass().getSimpleName());
        } catch (IOException e) {
            return new ProtocolError(e.getClass().getSimpleName());
        }

    }

    // We literally do not care about the value of this data,
    // so writing to it from multiple threads without barrier instructions

    private final byte[] buffer = new byte[8192];

    protected long consumeData(InputStream is, HttpGet request, Duration timeLimit) throws IOException, SocketTimeoutException {
        Instant start = Instant.now();
        Instant timeout = start.plus(timeLimit);
        long size = 0;

        while (true) {
            Duration remaining = Duration.between(Instant.now(), timeout);
            if (remaining.isNegative()) {
                request.abort();
                throw new SocketTimeoutException();
            }

            int n = is.read(buffer);

            if (n < 0) break;
            size += n;
        }

        return size;
    }
}
