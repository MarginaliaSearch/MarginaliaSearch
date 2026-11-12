package nu.marginalia.rss.svc;

import nu.marginalia.model.EdgeDomain;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static nu.marginalia.rss.svc.FeedFetcherService.DomainSkip.*;
import static org.junit.jupiter.api.Assertions.*;

class FeedFetcherCadenceTest {
    @Test
    void undatedFeedsRotate() {
        for (var entry : Map.of("wordpress.com", 4, "blogspot.com", 4, "substack.com", 7).entrySet()) {
            for (int i = 0; i < 100; i++) {
                var domain = new EdgeDomain("test" + i + "." + entry.getKey());
                int due = 0;
                for (int day = 20_000; day < 20_000 + entry.getValue(); day++) {
                    if (FeedFetcherService.shouldSkip(domain, 0, day) == NO_SKIP_TODAY) {
                        due++;
                    }
                }
                assertEquals(1, due, domain.toString());
            }
        }
    }

    @Test
    void knownFetchDate() {
        var domain = new EdgeDomain("test.substack.com");
        assertEquals(SKIP_TODAY, FeedFetcherService.shouldSkip(domain, 20_000, 20_006));
        assertEquals(NO_SKIP_TODAY, FeedFetcherService.shouldSkip(domain, 20_000, 20_007));
        assertEquals(NO_SKIP_TODAY, FeedFetcherService.shouldSkip(domain, 20_000, 20_008));
    }

    @Test
    void ordinaryDomain() {
        assertEquals(NEVER, FeedFetcherService.shouldSkip(new EdgeDomain("example.com"), 20_000, 20_000));
    }
}
