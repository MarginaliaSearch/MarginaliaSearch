package nu.marginalia.crawl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

public class CrawlLimiter {
    public static final int maxPoolSize = Integer.getInteger("crawler.pool-size", 512);

    private final Semaphore taskSemCount = new Semaphore(maxPoolSize);


    public void acquire() throws InterruptedException {
        // It's very important that we acquire the RAM semaphore first to avoid a deadlock
        taskSemCount.acquire(1);
    }

    public void release() {
        taskSemCount.release(1);
    }
}
