package nu.marginalia.ping.fetcher.response;

public record ProtocolError(String errorMessage) implements PingRequestResponse {
}
