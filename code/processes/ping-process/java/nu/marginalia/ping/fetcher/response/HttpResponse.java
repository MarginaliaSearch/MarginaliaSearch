package nu.marginalia.ping.fetcher.response;

import java.time.Duration;

public record HttpResponse(
        String version,
        int httpStatus,
        Headers headers,
        Duration httpResponseTime
) implements PingRequestResponse {
}
