package nu.marginalia.ping.fetcher.response;

public record TimeoutResponse(String errorMessage) implements PingRequestResponse {
}
