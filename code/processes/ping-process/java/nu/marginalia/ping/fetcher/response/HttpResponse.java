package nu.marginalia.ping.fetcher.response;

import java.time.Duration;

public record HttpResponse(
        String version,
        int httpStatus,
        byte[] body,
        Headers headers,
        Duration httpResponseTime
) implements PingRequestResponse {
}
