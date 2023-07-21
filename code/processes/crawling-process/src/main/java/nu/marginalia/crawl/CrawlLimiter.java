package nu.marginalia.crawl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

public class CrawlLimiter {
    public static final int maxPoolSize = Integer.getInteger("crawler.pool-size", 512);

    public record CrawlTaskLimits(Path refreshPath, boolean isRefreshable, int taskSize) {}

    private final Semaphore taskSemCount = new Semaphore(maxPoolSize);


    public CrawlTaskLimits getTaskLimits(Path fileName) {
        return new CrawlTaskLimits(fileName, true, 1);
    }


    public void acquire(CrawlTaskLimits properties) throws InterruptedException {
        // It's very important that we acquire the RAM semaphore first to avoid a deadlock
        taskSemCount.acquire(1);
    }

    public void release(CrawlTaskLimits properties) {
        taskSemCount.release(1);
    }
}
