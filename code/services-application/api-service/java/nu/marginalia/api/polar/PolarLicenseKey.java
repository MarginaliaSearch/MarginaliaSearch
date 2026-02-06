package nu.marginalia.api.polar;

import java.time.Instant;

public record PolarLicenseKey(
        String apiKey,
        String customerId,
        String benefitId,
        String status,
        Instant retrievalTs
) {
    public boolean isValid() {
        return "granted".equalsIgnoreCase(status);
    }
}
