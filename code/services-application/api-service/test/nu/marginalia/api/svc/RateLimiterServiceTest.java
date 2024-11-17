package nu.marginalia.api.svc;

import nu.marginalia.api.model.ApiLicense;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("flaky")
class RateLimiterServiceTest {

    RateLimiterService rateLimiterService;

    @BeforeEach
    public void setUp() {
        rateLimiterService = new RateLimiterService();
    }
    @AfterEach
    public void tearDown() {
        rateLimiterService.clear();
    }

    @Test
    public void testNoLimit() {

        var license = new ApiLicense("key", "Public Domain", "Steven", 0);

        for (int i = 0; i < 10000; i++) {
            assertTrue(rateLimiterService.isAllowed(license));
        }

        // No rate limiter is created when rate is <= 0
        assertEquals(0, rateLimiterService.size());
    }

    @Test
    public void testWithLimit() {

        var license = new ApiLicense("key", "Public Domain", "Steven", 10);
        var otherLicense = new ApiLicense("key2", "Public Domain", "Bob", 10);

        for (int i = 0; i < 1000; i++) {
            if (i < 10) {
                assertTrue(rateLimiterService.isAllowed(license));
            }
            else {
                assertFalse(rateLimiterService.isAllowed(license));
            }
        }

        assertTrue(rateLimiterService.isAllowed(otherLicense));
        assertEquals(2, rateLimiterService.size());
    }
}