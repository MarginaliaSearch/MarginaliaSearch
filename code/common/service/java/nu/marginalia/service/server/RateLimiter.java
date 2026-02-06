package nu.marginalia.service.server;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.*;
import java.time.temporal.ChronoUnit;

public class RateLimiter {
    private final Bucket bucket;

    public RateLimiter(Bucket bucket) {
        this.bucket = bucket;
    }

    public static RateLimiter queryPerMinuteLimiter(int perMinute) {
        int capacity = perMinute;
        int refillRate = perMinute;

        var refill = Refill.greedy(refillRate, Duration.ofMinutes(1));
        var bw = Bandwidth.classic(capacity, refill);

        return new RateLimiter(Bucket.builder().addLimit(bw).build());
    }

    public static RateLimiter queryPerDayLimiter(int perHour) {
        Instant firstRefill = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .plus(1, ChronoUnit.DAYS)
                .plusHours(8)
                .toInstant();

        Refill refill = Refill.intervallyAligned(perHour,
                Duration.ofDays(1),
                firstRefill,
                false);

        Bandwidth bw = Bandwidth.classic(perHour, refill);

        return new RateLimiter(Bucket.builder().addLimit(bw).build());
    }

    public boolean isAllowed() {
        return bucket.tryConsume(1);
    }

    public boolean hasMoreTokens() {
        return bucket.getAvailableTokens() <= 0;
    }

    public int availableCapacity() {
        return (int) bucket.getAvailableTokens();
    }
}
