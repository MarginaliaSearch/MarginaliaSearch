package nu.marginalia.crawl;

import nu.marginalia.crawl.CrawlerMain.CrawlSpecRecord;
import nu.marginalia.process.log.WorkLog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrawlerMainTest {

    @Test
    public void testLastEntry() throws IOException {
        Path dir = Files.createTempDirectory(getClass().getSimpleName());
        Path log = dir.resolve("crawler.log");
        try {
            // First pass records two domains.
            try (WorkLog wl = new WorkLog(log)) {
                wl.setJobToFinished("a.example.com", "/data/aa/aa/aaaa-a.example.com.slop.zip", 1);
                wl.setJobToFinished("b.example.com", "/data/bb/bb/bbbb-b.example.com.slop.zip", 2);
            }
            // A later partial pass appends a fresh entry for a.example.com without rotating the log.
            try (WorkLog wl = new WorkLog(log)) {
                wl.setJobToFinished("a.example.com", "/data/aa/aa/aaaa-a.example.com.slop.zip", 3);
            }

            CrawlerMain.compactCrawlerLog(log);

            Map<String, Integer> countByDomain = new HashMap<>();
            List<String> domains = new ArrayList<>();
            for (var entry : WorkLog.iterable(log)) {
                domains.add(entry.id());
                countByDomain.put(entry.id(), entry.cnt());
            }

            assertEquals(List.of("a.example.com", "b.example.com"), domains, "one entry per domain");
            assertEquals(3, countByDomain.get("a.example.com"), "the latest entry for a domain wins");
            assertEquals(2, countByDomain.get("b.example.com"));
        }
        finally {
            Files.deleteIfExists(log);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void testCrawlOrder() {
        List<CrawlSpecRecord> specs = new ArrayList<>(List.of(
                new CrawlSpecRecord("recent.example.com", 0),
                new CrawlSpecRecord("never.example.com", 0),
                new CrawlSpecRecord("old.example.com", 0)
        ));

        Map<String, Long> lastCrawlTimes = Map.of(
                "recent.example.com", 5000L,
                "old.example.com", 1000L
        );

        specs.sort(CrawlerMain.leastRecentlyCrawledFirst(lastCrawlTimes));

        assertEquals(
                List.of("never.example.com", "old.example.com", "recent.example.com"),
                specs.stream().map(CrawlSpecRecord::domain).toList());
    }

    @Test
    public void testTieBreak() {
        List<CrawlSpecRecord> specs = new ArrayList<>(List.of(
                new CrawlSpecRecord("b.example.com", 0),
                new CrawlSpecRecord("a.example.com", 0)
        ));

        specs.sort(CrawlerMain.leastRecentlyCrawledFirst(Map.of()));

        assertEquals(
                List.of("a.example.com", "b.example.com"),
                specs.stream().map(CrawlSpecRecord::domain).toList());
    }
}
