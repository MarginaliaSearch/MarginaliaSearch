package nu.marginalia.api.svc;

import nu.marginalia.api.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResponseCacheTest {
    ResponseCache responseCache;
    ApiLicense licenseA = new ApiLicense(
            "keyA",
            "Public Domain",
            "Steven",
            0
    );
    ApiLicense licenseB = new ApiLicense(
            "keyB",
            "Public Domain",
            "Jeff",
            0
    );

    ApiSearchResults resultsA = new ApiSearchResults("x", "y", Collections.emptyList());
    ApiSearchResults resultsB = new ApiSearchResults("x", "y", Collections.emptyList());

    @BeforeEach
    public void setUp() {
        responseCache = new ResponseCache();
    }

    @AfterEach
    public void tearDown() {
        responseCache.cleanUp();
    }

    @Test
    public void testSunnyDay() {
        responseCache.putResults(licenseA, "how do magnets work", "count=1", resultsA);

        var result = responseCache.getResults(licenseA, "how do magnets work", "count=1");

        assertTrue(result.isPresent());
        assertEquals(resultsA, result.get());
    }

    @Test
    public void testSunnyDay2() {
        responseCache.putResults(licenseA, "how do magnets work", "count=1", resultsA);
        responseCache.putResults(licenseA, "how do magnets work", "count=1", resultsB);

        var result = responseCache.getResults(licenseA, "how do magnets work", "count=1");

        assertTrue(result.isPresent());
        assertEquals(resultsB, result.get());
    }

    @Test
    public void testSunnyDay3() {
        responseCache.putResults(licenseA, "how do magnets work", "count=1", resultsA);
        responseCache.putResults(licenseA, "how many wives did Henry VIII have?", "count=1", resultsB);

        var result = responseCache.getResults(licenseA, "how do magnets work", "count=1");

        assertTrue(result.isPresent());
        assertEquals(resultsA, result.get());
    }

    @Test
    public void testSunnyDay4() {
        responseCache.putResults(licenseA, "how do magnets work", "count=1", resultsA);
        responseCache.putResults(licenseA, "how do magnets work", "count=2", resultsB);

        var result = responseCache.getResults(licenseA, "how do magnets work", "count=1");

        assertTrue(result.isPresent());
        assertEquals(resultsA, result.get());
    }

    @Test
    public void testSunnyDay5() {
        responseCache.putResults(licenseA, "how do magnets work", "count=1", resultsA);
        responseCache.putResults(licenseB, "how do magnets work", "count=1", resultsB);

        var result = responseCache.getResults(licenseA, "how do magnets work", "count=1");

        assertTrue(result.isPresent());
        assertEquals(resultsA, result.get());
    }
}