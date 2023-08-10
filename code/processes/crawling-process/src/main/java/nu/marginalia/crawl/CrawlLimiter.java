package nu.marginalia.crawl;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CrawlLimiter {
    public static final int maxPoolSize = Integer.getInteger("crawler.pool-size", 256);

    // Thresholds for throttling task-spawning. Note there's a bit of hysteresis to this
    private final long THROTTLE_TRIGGER_FREE_RAM = Runtime.getRuntime().maxMemory() / 4;
    private final long THROTTLE_RELEASE_FREE_RAM = Runtime.getRuntime().maxMemory() / 2;

    private final Semaphore taskSemCount = new Semaphore(maxPoolSize);

    // When set to true, the crawler will wait before starting additional tasks
    private final AtomicBoolean throttle = new AtomicBoolean(false);
    private static final Logger logger = LoggerFactory.getLogger(CrawlLimiter.class);

    public CrawlLimiter() {
        Thread monitorThread = new Thread(this::monitor, "Memory Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }


    @SneakyThrows
    public void monitor() {
        for (;;) {
            synchronized (throttle) {
                boolean oldThrottle = throttle.get();
                boolean newThrottle = oldThrottle;

                if (Runtime.getRuntime().maxMemory() == Long.MAX_VALUE) {
                    // According to the spec this may happen, although it seems to rarely
                    // be the case in practice
                    logger.warn("Memory based throttling disabled (set Xmx)");
                    return;
                }

                final long freeMemory = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory();

                if (oldThrottle && freeMemory > THROTTLE_RELEASE_FREE_RAM) {
                    newThrottle = false;
                    logger.warn("Memory based throttling released");
                }
                else if (!oldThrottle && freeMemory < THROTTLE_TRIGGER_FREE_RAM) {
                    newThrottle = true;
                    logger.warn("Memory based throttling triggered");

                    // Try to GC
                    System.gc();
                }


                throttle.set(newThrottle);

                if (!newThrottle) {
                    throttle.notifyAll();
                }
                if (newThrottle != oldThrottle) {
                    logger.warn("Memory based throttling set to {}", newThrottle);
                }
            }

            TimeUnit.SECONDS.sleep(1);
        }
    }

    @SneakyThrows
    public void waitForEnoughRAM() {
        while (throttle.get()) {
            synchronized (throttle) {
                throttle.wait(30000);
            }
        }
    }

}
