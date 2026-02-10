package nu.marginalia.ping.fetcher.response;

import java.security.cert.Certificate;
import java.time.Duration;

public record HttpsResponse(
        String version,
        int httpStatus,
        Headers headers,
        Certificate[] sslCertificates,
        SslMetadata sslMetadata,
        Duration httpResponseTime
) implements PingRequestResponse {
}
