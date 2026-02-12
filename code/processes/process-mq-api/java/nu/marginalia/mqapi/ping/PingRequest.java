package nu.marginalia.mqapi.ping;

import java.time.Instant;

public record PingRequest(String endTs) {
    public PingRequest(Instant endTs) {
        this(endTs.toString());
    }
}
