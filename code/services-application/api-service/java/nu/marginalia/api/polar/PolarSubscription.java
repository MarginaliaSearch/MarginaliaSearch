package nu.marginalia.api.polar;

import java.time.Instant;

public record PolarSubscription(
        String apiKey,
        String customerId,
        String status,
        Instant retrievalTs,
        Instant currentPeriodStart,
        Instant currentPeriodEnd
) {
}
