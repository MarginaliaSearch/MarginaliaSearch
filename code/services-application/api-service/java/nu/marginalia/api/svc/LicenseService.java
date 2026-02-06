package nu.marginalia.api.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.api.model.ApiLicenseOptions;
import nu.marginalia.api.polar.PolarBenefit;
import nu.marginalia.api.polar.PolarBenefits;
import nu.marginalia.api.polar.PolarClient;
import nu.marginalia.api.polar.PolarLicenseKey;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class LicenseService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HikariDataSource dataSource;
    private final PolarClient polarClient;
    private final PolarBenefits polarBenefits;
    private final ConcurrentHashMap<String, ApiLicense> licenseCache = new ConcurrentHashMap<>();

    @Inject
    public LicenseService(HikariDataSource dataSource,
                          PolarClient polarClient,
                          PolarBenefits polarBenefits
                          ) {
        this.dataSource = dataSource;
        this.polarClient = polarClient;
        this.polarBenefits = polarBenefits;
    }

    public static class NoSuchKeyException extends Exception {}

    @NotNull
    public ApiLicense getLicense(String key) throws NoSuchKeyException, IOException {
        var cachedLicense = licenseCache.get(key);
        if (cachedLicense != null) return cachedLicense;

        if (key.startsWith("POL")) {
            var polarLicense = getFromPolarSh(key);
            licenseCache.put(key, polarLicense);
            return polarLicense;
        }
        else { // DB license
            var dbLicense = getFromDb(key);
            licenseCache.put(key, dbLicense);
            return dbLicense;
        }
    }

    private ApiLicense getFromPolarSh(String key) throws NoSuchKeyException, IOException {
        PolarLicenseKey polarLicense = polarClient.validateLicenseKey(key).orElseThrow(NoSuchKeyException::new);
        PolarBenefit benefit = polarBenefits.getBenefit(polarLicense).orElseThrow(NoSuchKeyException::new);

        EnumSet<ApiLicenseOptions> options = EnumSet.of(
                ApiLicenseOptions.ADUIT_USAGE,
                ApiLicenseOptions.SOURCE_POLAR
                );

        if (benefit.allowOveruse()) {
            options.add(ApiLicenseOptions.ALLOW_DAILY_OVERUSE);
        }

        ApiLicense license = new ApiLicense(
                key,
                benefit.license(),
                key,
                benefit.ratePerMinMax(),
                benefit.rateDaily(),
                options
        );

        return license;
    }

    private ApiLicense getFromDb(String key) throws NoSuchKeyException {
        try (var conn = dataSource.getConnection();
            var stmt = conn.prepareStatement("SELECT LICENSE,NAME,RATE FROM EC_API_KEY WHERE LICENSE_KEY=?")) {

            stmt.setString(1, key);

            var rsp = stmt.executeQuery();

            if (rsp.next()) {
                return new ApiLicense(key,
                        rsp.getString("LICENSE"),
                        rsp.getString("NAME"),
                        rsp.getInt("RATE"),
                        0, // no daily limit for the DB licenses
                        EnumSet.of(
                                ApiLicenseOptions.ALLOW_V1_API,
                                ApiLicenseOptions.SOURCE_INTERNAL
                                )
                        );
            }

        }
        catch (Exception ex) {
            logger.error("Bad request", ex);
            throw new IllegalArgumentException();
        }

        throw new NoSuchKeyException();
    }

    public void flushCache() {
        licenseCache.clear();
    }
}
