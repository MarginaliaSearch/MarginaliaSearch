package nu.marginalia.crawl;

import nu.marginalia.crawl.CrawlerMain.CrawlSpecRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrawlerMainTest {

    @Test
    public void leastRecentlyCrawledFirstOrdersStalestFirst() {
        List<CrawlSpecRecord> specs = new ArrayList<>(List.of(
                new CrawlSpecRecord("recent.example.com", 0),
                new CrawlSpecRecord("never.example.com", 0),
                new CrawlSpecRecord("old.example.com", 0)
        ));

        Map<String, Long> lastCrawlTimes = Map.of(
                "recent.example.com", 5000L,
                "old.example.com", 1000L
                // never.example.com is absent -> treated as epoch 0
        );

        specs.sort(CrawlerMain.leastRecentlyCrawledFirst(lastCrawlTimes));

        assertEquals(
                List.of("never.example.com", "old.example.com", "recent.example.com"),
                specs.stream().map(CrawlSpecRecord::domain).toList());
    }

    @Test
    public void leastRecentlyCrawledFirstTieBreaksOnDomainName() {
        List<CrawlSpecRecord> specs = new ArrayList<>(List.of(
                new CrawlSpecRecord("b.example.com", 0),
                new CrawlSpecRecord("a.example.com", 0)
        ));

        // Both absent -> equal crawl time, so the deterministic tie-break on domain name decides.
        specs.sort(CrawlerMain.leastRecentlyCrawledFirst(Map.of()));

        assertEquals(
                List.of("a.example.com", "b.example.com"),
                specs.stream().map(CrawlSpecRecord::domain).toList());
    }
}
