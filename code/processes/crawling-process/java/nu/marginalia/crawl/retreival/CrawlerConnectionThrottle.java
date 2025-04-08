package nu.marginalia.crawl.retreival;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * This class is used to stagger the rate at which connections are created.
 * <p></p>
 * It is used to ensure that we do not create too many connections at once,
 * which can lead to network congestion and other issues.  Since the connections
 * tend to be very long-lived, we can afford to wait a bit before creating the next
 * even if it adds a bit of build-up time when the crawl starts.
 */
public class CrawlerConnectionThrottle {
    private Instant lastCrawlStart = Instant.EPOCH;
    private final Semaphore launchSemaphore = new Semaphore(1);

    private final Duration launchInterval;

    public CrawlerConnectionThrottle(Duration launchInterval) {
        this.launchInterval = launchInterval;
    }

    public void waitForConnectionPermission() throws InterruptedException {
        try {
            launchSemaphore.acquire();
            Instant nextPermittedLaunch = lastCrawlStart.plus(launchInterval);

            if (nextPermittedLaunch.isAfter(Instant.now())) {
                long waitTime = Duration.between(Instant.now(), nextPermittedLaunch).toMillis();
                TimeUnit.MILLISECONDS.sleep(waitTime);
            }

            lastCrawlStart = Instant.now();
        }
        finally {
            launchSemaphore.release();
        }
    }
}
