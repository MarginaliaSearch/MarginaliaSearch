package nu.marginalia.crawl.fetcher;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class HttpFetcherImplRetryAfterTest {

    @Test
    void parseRetryAfterSeconds_numericInteger() {
        assertEquals(5, HttpFetcherImpl.parseRetryAfterSeconds("5"));
    }

    @Test
    void parseRetryAfterSeconds_numericDecimal() {
        assertEquals(3, HttpFetcherImpl.parseRetryAfterSeconds("2.7"));
    }

    @Test
    void parseRetryAfterSeconds_zero() {
        assertEquals(0, HttpFetcherImpl.parseRetryAfterSeconds("0"));
    }

    @Test
    void parseRetryAfterSeconds_httpDate() {
        // Create a date 30 seconds in the future
        ZonedDateTime future = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30);
        String httpDate = future.format(DateTimeFormatter.RFC_1123_DATE_TIME);

        int result = HttpFetcherImpl.parseRetryAfterSeconds(httpDate);

        // Allow some tolerance for test execution time
        assertTrue(result >= 28 && result <= 32,
                "Expected ~30 seconds but got " + result);
    }

    @Test
    void parseRetryAfterSeconds_httpDateInPast() {
        ZonedDateTime past = ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(10);
        String httpDate = past.format(DateTimeFormatter.RFC_1123_DATE_TIME);

        int result = HttpFetcherImpl.parseRetryAfterSeconds(httpDate);
        assertEquals(0, result);
    }

    @Test
    void parseRetryAfterSeconds_null() {
        assertEquals(-1, HttpFetcherImpl.parseRetryAfterSeconds(null));
    }

    @Test
    void parseRetryAfterSeconds_garbage() {
        assertEquals(-1, HttpFetcherImpl.parseRetryAfterSeconds("not-a-date-or-number"));
    }

    @Test
    void parseRetryAfterSeconds_realWorldExample() {
        // The format from the error message: "Thu, 05 Mar 2026 23:25:46 +0000"
        // This is a past date relative to test execution, so should return 0
        int result = HttpFetcherImpl.parseRetryAfterSeconds("Thu, 05 Mar 2026 23:25:46 +0000");
        assertTrue(result >= 0, "Should parse HTTP-date without error, got " + result);
    }
}
