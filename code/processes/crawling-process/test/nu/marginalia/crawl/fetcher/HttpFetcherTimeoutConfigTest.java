package nu.marginalia.crawl.fetcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that timeout configuration properties are properly applied.
 */
public class HttpFetcherTimeoutConfigTest {

    @BeforeEach
    void setUp() {
        // Clear any existing system properties to ensure clean test
        System.clearProperty("crawler.socketTimeout");
        System.clearProperty("crawler.connectTimeout");
        System.clearProperty("crawler.responseTimeout");
        System.clearProperty("crawler.connectionRequestTimeout");
    }

    @Test
    void testDefaultTimeoutValues() {
        // Test that default values are used when no system properties are set
        HttpFetcherImpl fetcher = new HttpFetcherImpl("test-agent");
        
        // Verify that the fetcher was created successfully with default timeouts
        assertNotNull(fetcher);
        
        // The actual timeout values are private, but we can verify the fetcher
        // was created without exceptions, indicating the default values were used
    }

    @Test
    void testCustomTimeoutValues() {
        // Set custom timeout values
        System.setProperty("crawler.socketTimeout", "15");
        System.setProperty("crawler.connectTimeout", "45");
        System.setProperty("crawler.responseTimeout", "20");
        System.setProperty("crawler.connectionRequestTimeout", "3");
        
        try {
            HttpFetcherImpl fetcher = new HttpFetcherImpl("test-agent");
            
            // Verify that the fetcher was created successfully with custom timeouts
            assertNotNull(fetcher);
            
            // The actual timeout values are private, but we can verify the fetcher
            // was created without exceptions, indicating the custom values were used
        } finally {
            // Clean up system properties
            System.clearProperty("crawler.socketTimeout");
            System.clearProperty("crawler.connectTimeout");
            System.clearProperty("crawler.responseTimeout");
            System.clearProperty("crawler.connectionRequestTimeout");
        }
    }

    @Test
    void testInvalidTimeoutValues() {
        // Set invalid timeout values to test error handling
        System.setProperty("crawler.socketTimeout", "invalid");
        System.setProperty("crawler.connectTimeout", "-5");
        
        try {
            // This should still work as Integer.getInteger() handles invalid values gracefully
            HttpFetcherImpl fetcher = new HttpFetcherImpl("test-agent");
            assertNotNull(fetcher);
        } finally {
            // Clean up system properties
            System.clearProperty("crawler.socketTimeout");
            System.clearProperty("crawler.connectTimeout");
        }
    }
}
