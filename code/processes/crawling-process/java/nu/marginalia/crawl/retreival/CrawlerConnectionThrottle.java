package nu.marginalia.crawl.retreival;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;

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
    private final Duration throttleDuration;

    private Instant disableTime = null;

    private volatile boolean doThrottle = true;

    public CrawlerConnectionThrottle(Duration launchInterval,
                                     Duration throttleDuration)
    {
        this.launchInterval = launchInterval;
        this.throttleDuration = throttleDuration;
    }

    public void waitForConnectionPermission() throws InterruptedException {
        if (!doThrottle)
            return;

        try {
            launchSemaphore.acquire();

            Instant now = Instant.now();

            if (disableTime == null) disableTime = now.plus(throttleDuration);
            else if (now.isAfter(disableTime)) {
                doThrottle = false;
                return;
            }

            Instant nextPermittedLaunch = lastCrawlStart.plus(launchInterval);

            Thread.sleep(Duration.between(now, nextPermittedLaunch));

            lastCrawlStart = Instant.now();
        }
        finally {
            launchSemaphore.release();
        }
    }
}
