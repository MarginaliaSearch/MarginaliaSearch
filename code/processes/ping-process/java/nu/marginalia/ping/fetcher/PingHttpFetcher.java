package nu.marginalia.ping.fetcher;

import com.google.inject.Inject;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.ping.fetcher.response.*;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
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

        var builder = ClassicRequestBuilder.create(method.name())
                .setUri(url)
                .addHeader("User-Agent", userAgent.uaString())
                .addHeader("Accept-Encoding", "gzip");
        if (etag != null) {
            builder.addHeader("If-None-Match", etag);
        }
        if (lastModified != null) {
            builder.addHeader("If-Modified-Since", lastModified);
        }

        var req = builder.build();

        HttpClientContext context = HttpClientContext.create();
        try {
            Instant start = Instant.now();
            return client.execute(req, context, (rsp) -> {

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

                    byte[] body = entity != null ? EntityUtils.toByteArray(entity) : null;

                    Duration responseTime = Duration.between(start, Instant.now());

                    return PingRequestResponse.of(
                            rsp.getVersion(),
                            rsp.getCode(),
                            body,
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
        } catch (IOException e) {
            return new ConnectionError(e.getMessage());
        }
    }

}
