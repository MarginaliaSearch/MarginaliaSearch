package nu.marginalia.crawl.retreival;

import lombok.SneakyThrows;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class CrawlDelayTimer {

    // When no crawl delay is specified, lean toward twice the fetch+process time, within these limits:
    private static final long DEFAULT_CRAWL_DELAY_MIN_MS = Long.getLong("defaultCrawlDelay", 1000);
    private static final long DEFAULT_CRAWL_DELAY_MAX_MS = Long.getLong("defaultCrawlDelaySlow", 2500);

    /** Flag to indicate that the crawler should slow down, e.g. from 429s */
    private boolean slowDown = false;

    private final long delayTime;

    public CrawlDelayTimer(long delayTime) {
        this.delayTime = delayTime;
    }

    @SneakyThrows
    public void delay(long spentTime) {
        long sleepTime = delayTime;

        if (sleepTime >= 1) {
            if (spentTime > sleepTime)
                return;

            Thread.sleep(min(sleepTime - spentTime, 5000));
        }
        else if (slowDown) {
            // Additional delay when the server is signalling it wants slower requests
            Thread.sleep( DEFAULT_CRAWL_DELAY_MIN_MS);
        }
        else {
            // When no crawl delay is specified, lean toward twice the fetch+process time,
            // within sane limits. This means slower servers get slower crawling, and faster
            // servers get faster crawling.

            sleepTime = spentTime * 2;
            sleepTime = min(sleepTime, DEFAULT_CRAWL_DELAY_MAX_MS);
            sleepTime = max(sleepTime, DEFAULT_CRAWL_DELAY_MIN_MS);

            if (spentTime > sleepTime)
                return;

            Thread.sleep(sleepTime - spentTime);
        }
    }

    /** Increase the delay between requests if the server is signalling it wants slower requests with HTTP 429 */
    public void slowDown() {
        slowDown = true;
    }
}
