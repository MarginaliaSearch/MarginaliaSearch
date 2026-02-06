package nu.marginalia.api.svc;

import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.api.model.ApiLicenseOptions;
import nu.marginalia.api.polar.PolarClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

@Tag("flaky")
class RateLimiterServiceTest {

    RateLimiterService rateLimiterService;

    @BeforeEach
    public void setUp() {
        rateLimiterService = new RateLimiterService(PolarClient.asDisabled());
    }

    @AfterEach
    public void tearDown() {
        rateLimiterService.clear();
    }

    @Test
    public void testNoLimit() {

        var license = new ApiLicense("key", "Public Domain", "Steven", 0, 0,
                EnumSet.of(ApiLicenseOptions.ALLOW_DAILY_OVERUSE, ApiLicenseOptions.ALLOW_V1_API));

        for (int i = 0; i < 10000; i++) {
            assertTrue(rateLimiterService.isAllowedQPM(license));
        }

        // No rate limiter is created when rate is <= 0
        assertEquals(0, rateLimiterService.size());
    }

    @Test
    public void testWithLimit() {

        var license = new ApiLicense("key", "Public Domain", "Steven", 10, 0,  EnumSet.of(ApiLicenseOptions.ALLOW_DAILY_OVERUSE, ApiLicenseOptions.ALLOW_V1_API));
        var otherLicense = new ApiLicense("key2", "Public Domain", "Bob", 10, 0,  EnumSet.of(ApiLicenseOptions.ALLOW_DAILY_OVERUSE, ApiLicenseOptions.ALLOW_V1_API));

        for (int i = 0; i < 1000; i++) {
            if (i < 10) {
                assertTrue(rateLimiterService.isAllowedQPM(license));
            }
            else {
                assertFalse(rateLimiterService.isAllowedQPM(license));
            }
        }

        assertTrue(rateLimiterService.isAllowedQPM(otherLicense));
        assertEquals(2, rateLimiterService.size());
    }
}