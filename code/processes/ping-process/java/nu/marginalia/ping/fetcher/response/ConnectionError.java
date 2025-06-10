package nu.marginalia.ping.fetcher.response;

public record ConnectionError(String errorMessage) implements PingRequestResponse {
}
