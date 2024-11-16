package nu.marginalia.crawl.retreival;

import nu.marginalia.crawl.fetcher.HttpFetcherImpl;

import java.time.Duration;

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

    /** Call when we've gotten an HTTP 429 response.  This will wait a moment, and then
     * set a flag that slows down the main crawl delay as well. */
    public void waitRetryDelay(HttpFetcherImpl.RateLimitException ex) throws InterruptedException {
        slowDown = true;

        Duration delay = ex.retryAfter();

        if (delay.compareTo(Duration.ofSeconds(1)) < 0) {
            // If the server wants us to retry in less than a second, we'll just wait a bit
            delay = Duration.ofSeconds(1);
        }
        else if (delay.compareTo(Duration.ofSeconds(5)) > 0) {
            // If the server wants us to retry in more than a minute, we'll wait a bit
            delay = Duration.ofSeconds(5);
        }

        Thread.sleep(delay.toMillis());
    }

    public void waitFetchDelay(long spentTime) {
        long sleepTime = delayTime;

        try {
            if (sleepTime >= 1) {
                if (spentTime > sleepTime)
                    return;

                Thread.sleep(min(sleepTime - spentTime, 5000));
            } else {
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

            if (slowDown) {
                // Additional delay when the server is signalling it wants slower requests
                Thread.sleep(DEFAULT_CRAWL_DELAY_MIN_MS);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException();
        }
    }
}
