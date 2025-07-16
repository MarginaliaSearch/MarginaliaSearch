package nu.marginalia.ping;

import com.google.inject.Inject;
import nu.marginalia.ping.model.ErrorClassification;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class BackoffStrategy {

    private final Map<ErrorClassification, Duration> baseIntervals;
    private final Map<ErrorClassification, Duration> maxIntervals;
    private final Duration okInterval;

    @Inject
    public BackoffStrategy(PingIntervalsConfiguration pingIntervalsConfiguration) {
        this.baseIntervals = pingIntervalsConfiguration.baseIntervals();
        this.maxIntervals = pingIntervalsConfiguration.maxIntervals();
        this.okInterval = baseIntervals.get(ErrorClassification.NONE);
    }

    public Duration getOkInterval() {
        return okInterval;
    }

    public Duration getUpdateTime(Duration currentDuration,
                           ErrorClassification errorClassification,
                           int backoffConsecutiveFailures) {

        Duration nextBackoff = calculateBackoff(errorClassification, currentDuration, backoffConsecutiveFailures + 1);
        nextBackoff = addJitter(nextBackoff);

        return nextBackoff;
    }

    private Duration calculateBackoff(ErrorClassification errorClassification,
                                       Duration currentDuration,
                                       int backoffConsecutiveFailures) {

        if (currentDuration == null) {
            return baseIntervals.get(errorClassification);
        }

        Duration baseInterval = baseIntervals.get(errorClassification);
        Duration maxInterval = maxIntervals.get(errorClassification);

        if (currentDuration.compareTo(maxInterval) >= 0) {
            return maxInterval;
        }

        double multiplier = switch(errorClassification) {
            case ErrorClassification.UNKNOWN -> 1.5;
            case ErrorClassification.TIMEOUT -> 2.5;
            case ErrorClassification.CONNECTION_ERROR -> 2.0;
            case ErrorClassification.HTTP_CLIENT_ERROR -> 1.7;
            case ErrorClassification.HTTP_SERVER_ERROR -> 2.0;
            case ErrorClassification.SSL_ERROR -> 1.8;
            case ErrorClassification.DNS_ERROR -> 1.5;
            default -> 2.0;  // Default multiplier for any other classification
        };

        double backoffMinutes = baseInterval.toMinutes()
                * Math.pow(multiplier, Math.clamp(backoffConsecutiveFailures, 1, 10));

        Duration newDuration = Duration.ofMinutes(Math.round(0.5+backoffMinutes));
        if (newDuration.compareTo(maxInterval) > 0) {
            return maxInterval;
        }

        return newDuration;
    }

    private Duration addJitter(Duration duration) {
        // Add ±15% jitter to prevent synchronized retries
        double jitterPercent = 0.15;
        long baseMinutes = duration.toMinutes();
        long jitterRange = (long) (baseMinutes * jitterPercent * 2);
        long jitterOffset = ThreadLocalRandom.current().nextLong(jitterRange + 1) - (jitterRange / 2);

        long finalMinutes = Math.max(1, baseMinutes + jitterOffset);
        return Duration.ofMinutes(finalMinutes);
    }
}