package nu.marginalia.api.svc;

import com.google.inject.Singleton;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.service.server.RateLimiter;

import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class RateLimiterService {

    private final ConcurrentHashMap<ApiLicense, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public boolean isAllowed(ApiLicense license) {
        if (license.rate <= 0)
            return true;

        return rateLimiters
                .computeIfAbsent(license, this::newLimiter)
                .isAllowed();
    }

    public RateLimiter newLimiter(ApiLicense license) {
        return RateLimiter.custom(license.rate);
    }

}
