package nu.marginalia.ping;

import nu.marginalia.ping.model.ErrorClassification;

import java.time.Duration;
import java.util.Map;

public record PingIntervalsConfiguration(
        Duration dnsUpdateInterval,
        Map<ErrorClassification, Duration> baseIntervals,
        Map<ErrorClassification, Duration> maxIntervals
) {
}
