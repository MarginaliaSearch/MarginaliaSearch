package nu.marginalia.api.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.api.model.ApiLicenseOptions;
import nu.marginalia.api.model.DailyLimitState;
import nu.marginalia.api.polar.PolarClient;
import nu.marginalia.service.server.RateLimiter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Singleton
public class RateLimiterService {

    private final ConcurrentHashMap<ApiLicense, RateLimiter> perMinuteLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ApiLicense, RateLimiter> perDayLimiters = new ConcurrentHashMap<>();
    private final PolarClient polarClient;

    private volatile ConcurrentHashMap<ApiLicense, Integer> overuse = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<ApiLicense, Integer> use = new ConcurrentHashMap<>();

    @Inject
    public RateLimiterService(PolarClient polarClient) {
        this.polarClient = polarClient;

        if (polarClient.isAvilable()) {
            Executors.newScheduledThreadPool(1)
                    .scheduleWithFixedDelay(this::reportUsage, 5, 5, TimeUnit.MINUTES);
        }
    }

    public void reportUsage() {
        Instant snapshotTime = Instant.now();

        var currentUse = use;
        use = new ConcurrentHashMap<>();

        var currentOveruse = overuse;
        overuse = new ConcurrentHashMap<>();

        Set<ApiLicense> reportedKeys = new HashSet<>();

        for (var key : currentUse.keySet()) {
            if (!reportedKeys.add(key))
                continue;

            int use = currentUse.getOrDefault(key, 0);
            int overuse = currentOveruse.getOrDefault(key, 0);

            polarClient.reportKeyUse(key.key(), snapshotTime, use, overuse);

            try {
                TimeUnit.SECONDS.sleep(1);
            }
            catch (InterruptedException ex) {
                ex.printStackTrace();
                return;
            }
        }

        for (var key : currentOveruse.keySet()) {
            if (!reportedKeys.add(key))
                continue;

            int use = currentUse.getOrDefault(key, 0);
            int overuse = currentOveruse.getOrDefault(key, 0);

            polarClient.reportKeyUse(key.key(), snapshotTime, use, overuse);

            try {
                TimeUnit.SECONDS.sleep(1);
            }
            catch (InterruptedException ex) {
                ex.printStackTrace();
                return;
            }
        }

    }

    public boolean isAllowedQPM(ApiLicense license) {

        int qpm = license.ratePerMinute();

        if (qpm <= 0)
            return true;

        return getMinuteLimiter(license).isAllowed();
    }

    public boolean isAllowedQPD(ApiLicense license) {

        if (license.hasOption(ApiLicenseOptions.ALLOW_DAILY_OVERUSE)) {
            return true;
        }

        int qpd = license.ratePerDay();

        if (qpd <= 0) {
            return true;
        }

        return getDailyLimiter(license).isAllowed();
    }


    public DailyLimitState registerSuccessfulQuery(ApiLicense license) {

        if (license.hasOption(ApiLicenseOptions.ADUIT_USAGE)) {
            use.merge(license, 1, Integer::sum);
        }

        int qpd = license.ratePerDay();

        if (qpd <= 0)
            return new DailyLimitState.UnderLimit(Integer.MAX_VALUE);

        RateLimiter limiter = getDailyLimiter(license);

        if (!license.hasOption(ApiLicenseOptions.ALLOW_DAILY_OVERUSE)) {

            // If daily overuse is not allowed, we've already tested this rate limiter
            // before the query, and shouldn't repeat that as it would double count the queries,
            // instead just return an estimate on remaining capacity!

            return new DailyLimitState.UnderLimit(limiter.availableCapacity());
        }

        if (limiter.isAllowed()) {
            return new DailyLimitState.UnderLimit(limiter.availableCapacity());
        }
        else {
            overuse.merge(license, 1, Integer::sum);
            return new DailyLimitState.OverLimitCharge();
        }
    }

    private RateLimiter getMinuteLimiter(ApiLicense license) {
        int qpm = license.ratePerMinute();
        return perMinuteLimiters.computeIfAbsent(license, (l) -> RateLimiter.queryPerMinuteLimiter(qpm));
    }

    private RateLimiter getDailyLimiter(ApiLicense license) {
        int qpd = license.ratePerDay();
        return perDayLimiters.computeIfAbsent(license, (l) -> RateLimiter.queryPerDayLimiter(qpd));
    }


    public int remainingDailyLimit(ApiLicense license) {
        int qpd = license.ratePerDay();
        if (qpd <= 0)
            return Integer.MAX_VALUE;

        return getDailyLimiter(license).availableCapacity();
    }


    public void clear() {
        perMinuteLimiters.clear();
    }

    public int size() {
        return perMinuteLimiters.size();
    }
}
