package nu.marginalia.service.server;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// FIXME: This is very overengineered and needs a review
public class RateLimiter {

    private final Map<String, Bucket> bucketMap = new ConcurrentHashMap<>();

    private final int capacity;
    private final int refillRate;

    public RateLimiter(int capacity, int refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;

        Thread.ofVirtual()
                .name("rate-limiter-cleaner")
                .start(() -> {
                    while (true) {
                        cleanIdleBuckets();
                        try {
                            TimeUnit.MINUTES.sleep(30);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
    }


    public static RateLimiter custom(int perMinute) {
        return new RateLimiter(4 * perMinute, perMinute);
    }

    private void cleanIdleBuckets() {
        bucketMap.clear();
    }

    public boolean isAllowed() {
        return bucketMap.computeIfAbsent("any",
                (ip) -> createBucket()).tryConsume(1);
    }

    private Bucket createBucket() {
        var refill = Refill.greedy(refillRate, Duration.ofSeconds(60));
        var bw = Bandwidth.classic(capacity, refill);
        return Bucket.builder().addLimit(bw).build();
    }
}
