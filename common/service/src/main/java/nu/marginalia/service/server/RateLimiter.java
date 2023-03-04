package nu.marginalia.service.server;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.client.Context;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RateLimiter {

    private final Map<String, Bucket> bucketMap = new ConcurrentHashMap<>();

    private final int capacity;
    private final int refillRate;

    public RateLimiter(int capacity, int refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;

        Schedulers.io().schedulePeriodicallyDirect(this::cleanIdleBuckets, 30, 30, TimeUnit.MINUTES);
    }


    public static RateLimiter forExpensiveRequest() {
        return new RateLimiter(5, 10);
    }

    public static RateLimiter custom(int perMinute) {
        return new RateLimiter(perMinute, 60);
    }

    public static RateLimiter forSpamBots() {
        return new RateLimiter(120, 3600);
    }


    public static RateLimiter forLogin() {
        return new RateLimiter(3, 15);
    }

    private void cleanIdleBuckets() {
        bucketMap.clear();
    }

    public boolean isAllowed(Context ctx) {
        if (!ctx.isPublic()) { // Internal server->server request
            return true;
        }

        return bucketMap.computeIfAbsent(ctx.getContextId(),
                (ip) -> createBucket()).tryConsume(1);
    }

    public boolean isAllowed() {
        return bucketMap.computeIfAbsent("any",
                (ip) -> createBucket()).tryConsume(1);
    }
    private Bucket createBucket() {
        var refill = Refill.greedy(1, Duration.ofSeconds(refillRate));
        var bw = Bandwidth.classic(capacity, refill);
        return Bucket.builder().addLimit(bw).build();
    }
}
