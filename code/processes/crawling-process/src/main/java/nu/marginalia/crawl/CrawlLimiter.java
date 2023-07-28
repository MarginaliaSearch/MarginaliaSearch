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
    private static final long THROTTLE_TRIGGER_FREE_RAM = 2 * 1024 * 1024 * 1024L;
    private static final long THROTTLE_RELEASE_FREE_RAM = 4 * 1024 * 1024 * 1024L;

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

    private void waitForEnoughRAM() throws InterruptedException {
        while (!throttle.get()) {
            synchronized (throttle) {
                throttle.wait(30000);
            }
        }
    }

    public void acquire() throws InterruptedException {
        taskSemCount.acquire(1);

        if (taskSemCount.availablePermits() < maxPoolSize / 2) {
            waitForEnoughRAM();
        }
    }

    public void release() {
        taskSemCount.release(1);
    }
}
