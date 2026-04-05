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
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Singleton
public class RateLimiterService {

    private final ConcurrentHashMap<ApiLicense, RateLimiter> perMinuteLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ApiLicense, RateLimiter> perDayLimiters = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<ApiLicense, RateLimiter> siteInfoPerMinuteLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ApiLicense, RateLimiter> siteInfoPerDayLimiters = new ConcurrentHashMap<>();

    private final PolarClient polarClient;


    private volatile ConcurrentHashMap<ApiLicense, Integer> overuse = new ConcurrentHashMap<>();

    // Overuse that's currently in flight being reported, helps maintain a better estimate
    private volatile ConcurrentHashMap<ApiLicense, Integer> reportingOveruse = new ConcurrentHashMap<>();

    private volatile ConcurrentHashMap<ApiLicense, Integer> use = new ConcurrentHashMap<>();

    private volatile ConcurrentHashMap<ApiLicense, Integer> siteInfoOveruse = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<ApiLicense, Integer> reportingSiteInfoOveruse = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<ApiLicense, Integer> siteInfoUse = new ConcurrentHashMap<>();

    @Inject
    public RateLimiterService(PolarClient polarClient) {
        this.polarClient = polarClient;

        if (polarClient.isAvilable()) {
            Executors.newScheduledThreadPool(1)
                    .scheduleWithFixedDelay(this::reportUsage, 1, 5, TimeUnit.MINUTES);
        }
    }

    public void reportUsage() {
        Instant snapshotTime = Instant.now();

        var currentUse = use;
        use = new ConcurrentHashMap<>();

        var currentOveruse = overuse;
        reportingOveruse.putAll(overuse);
        overuse = new ConcurrentHashMap<>();

        var currentSiteInfoUse = siteInfoUse;
        siteInfoUse = new ConcurrentHashMap<>();

        var currentSiteInfoOveruse = siteInfoOveruse;
        reportingSiteInfoOveruse.putAll(siteInfoOveruse);
        siteInfoOveruse = new ConcurrentHashMap<>();

        Set<ApiLicense> allKeys = new HashSet<>();
        allKeys.addAll(currentUse.keySet());
        allKeys.addAll(currentOveruse.keySet());
        allKeys.addAll(currentSiteInfoUse.keySet());
        allKeys.addAll(currentSiteInfoOveruse.keySet());

        for (ApiLicense key : allKeys) {
            int queryUse = currentUse.getOrDefault(key, 0);
            int queryOveruse = currentOveruse.getOrDefault(key, 0);
            int siUse = currentSiteInfoUse.getOrDefault(key, 0);
            int siOveruse = currentSiteInfoOveruse.getOrDefault(key, 0);

            polarClient.reportKeyUse(key.key(), snapshotTime, queryUse, queryOveruse, siUse, siOveruse);
            reportingOveruse.remove(key);
            reportingSiteInfoOveruse.remove(key);

            try {
                TimeUnit.SECONDS.sleep(1);
            }
            catch (InterruptedException ex) {
                ex.printStackTrace();
                return;
            }
        }

        reportingOveruse.clear();
        reportingSiteInfoOveruse.clear();
    }

    /** Return an estimate of how much billable API overuse has been reported */
    public long estimatedTotalApiUseForPeriod(ApiLicense license) {
        if (!license.hasOption(ApiLicenseOptions.ALLOW_QUERY_DAILY_OVERUSE))
            return 0;

        OptionalLong reportedOveruse = polarClient.getApiOveruseEstimate(license);

        return reportedOveruse.orElse(0L)
                + overuse.getOrDefault(license, 0)
                + reportingOveruse.getOrDefault(license, 0);
    }

    public boolean isAllowedQPM(ApiLicense license) {

        int qpm = license.ratePerMinute();

        if (qpm <= 0)
            return true;

        return getMinuteLimiter(license).isAllowed();
    }

    public boolean isAllowedQPD(ApiLicense license) {

        if (license.hasOption(ApiLicenseOptions.ALLOW_QUERY_DAILY_OVERUSE)) {
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

        if (!license.hasOption(ApiLicenseOptions.ALLOW_QUERY_DAILY_OVERUSE)) {

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

    public boolean isAllowedSiteInfoQPM(ApiLicense license) {
        int qpm = license.siteInfoRatePerMinute();

        if (qpm <= 0)
            return true;

        return getSiteInfoMinuteLimiter(license).isAllowed();
    }

    public boolean isAllowedSiteInfoQPD(ApiLicense license) {
        if (license.hasOption(ApiLicenseOptions.ALLOW_SITE_INFO_DAILY_OVERUSE)) {
            return true;
        }

        int qpd = license.siteInfoRatePerDay();

        if (qpd <= 0) {
            return true;
        }

        return getSiteInfoDailyLimiter(license).isAllowed();
    }

    public DailyLimitState registerSuccessfulSiteInfoQuery(ApiLicense license) {
        if (license.hasOption(ApiLicenseOptions.ADUIT_USAGE)) {
            siteInfoUse.merge(license, 1, Integer::sum);
        }

        int qpd = license.siteInfoRatePerDay();

        if (qpd <= 0)
            return new DailyLimitState.UnderLimit(Integer.MAX_VALUE);

        RateLimiter limiter = getSiteInfoDailyLimiter(license);

        if (!license.hasOption(ApiLicenseOptions.ALLOW_SITE_INFO_DAILY_OVERUSE)) {
            return new DailyLimitState.UnderLimit(limiter.availableCapacity());
        }

        if (limiter.isAllowed()) {
            return new DailyLimitState.UnderLimit(limiter.availableCapacity());
        }
        else {
            siteInfoOveruse.merge(license, 1, Integer::sum);
            return new DailyLimitState.OverLimitCharge();
        }
    }

    public int remainingSiteInfoDailyLimit(ApiLicense license) {
        int qpd = license.siteInfoRatePerDay();
        if (qpd <= 0)
            return Integer.MAX_VALUE;

        return getSiteInfoDailyLimiter(license).availableCapacity();
    }

    /** Return an estimate of how much billable site info overuse has been reported */
    public long estimatedTotalSiteInfoUseForPeriod(ApiLicense license) {
        if (!license.hasOption(ApiLicenseOptions.ALLOW_SITE_INFO_DAILY_OVERUSE))
            return 0;

        OptionalLong reportedOveruse = polarClient.getSiteInfoOveruseEstimate(license);

        return reportedOveruse.orElse(0L)
                + siteInfoOveruse.getOrDefault(license, 0)
                + reportingSiteInfoOveruse.getOrDefault(license, 0);
    }

    private RateLimiter getMinuteLimiter(ApiLicense license) {
        int qpm = license.ratePerMinute();
        return perMinuteLimiters.computeIfAbsent(license, (l) -> RateLimiter.queryPerMinuteLimiter(qpm));
    }

    private RateLimiter getDailyLimiter(ApiLicense license) {
        int qpd = license.ratePerDay();
        return perDayLimiters.computeIfAbsent(license, (l) -> RateLimiter.queryPerDayLimiter(qpd));
    }

    private RateLimiter getSiteInfoMinuteLimiter(ApiLicense license) {
        int qpm = license.siteInfoRatePerMinute();
        return siteInfoPerMinuteLimiters.computeIfAbsent(license, (l) -> RateLimiter.queryPerMinuteLimiter(qpm));
    }

    private RateLimiter getSiteInfoDailyLimiter(ApiLicense license) {
        int qpd = license.siteInfoRatePerDay();
        return siteInfoPerDayLimiters.computeIfAbsent(license, (l) -> RateLimiter.queryPerDayLimiter(qpd));
    }


    public int remainingDailyLimit(ApiLicense license) {
        int qpd = license.ratePerDay();
        if (qpd <= 0)
            return Integer.MAX_VALUE;

        return getDailyLimiter(license).availableCapacity();
    }

    public boolean hasRemainingDailyLimit(ApiLicense license) {
        return remainingDailyLimit(license) > 0;
    }

    public void clear() {
        perMinuteLimiters.clear();
        siteInfoPerMinuteLimiters.clear();
    }

    public int size() {
        return perMinuteLimiters.size();
    }
}
