package nu.marginalia.crawl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

public class CrawlLimiter {
    public static final int maxPoolSize = Integer.getInteger("crawler.pool-size", 512);

    // We'll round up to this size when we're crawling a new domain to prevent
    // too many concurrent connections
    public static final int minCrawlDataSizeKb = 128; // 100 Kb

    // The largest size on disk where we'll permit a refresh crawl
    // (these files easily grow into the gigabytes, we don't want that in RAM)
    public static  final int maxRefreshableCrawlDataSizeKBytes = 1024*128; // 128 Mb

    // This limits how many concurrent crawl tasks we can have running at once
    // based on their size on disk.  The on-disk size is compressed, and the
    // in-ram size is partially compressed (i.e. only the document body); so
    // maybe a fair estimate is something like 2-4x this figure for RAM usage
    //
    public static final int maxConcurrentCrawlTaskSizeKb = 512*1024; // 512 Mb

    static {
        // Sanity check; if this is false we'll get a deadlock on taskSemRAM
        assert maxConcurrentCrawlTaskSizeKb >= maxRefreshableCrawlDataSizeKBytes
                : "maxConcurrentCrawlTaskSizeKb must be larger than maxRefreshableCrawlDataSizeKBytes";
    }

    public record CrawlTaskLimits(Path refreshPath, boolean isRefreshable, int taskSize) {}

    // We use two semaphores to keep track of the number of concurrent crawls;
    // first a RAM sempahore to limit the amount of RAM used by refresh crawls.
    // then a count semaphore to limit the number of concurrent threads (this keeps the connection count manageable)
    private final Semaphore taskSemRAM = new Semaphore(maxConcurrentCrawlTaskSizeKb);
    private final Semaphore taskSemCount = new Semaphore(maxPoolSize);


    public CrawlTaskLimits getTaskLimits(Path fileName) {
        long size;

        try {
            size = Math.max(minCrawlDataSizeKb, Files.size(fileName) / 1024);
        } catch (IOException ex) {
            // If we can't read the file, we'll assume it's small since we won't be able to read it later for the refresh either
            return new CrawlTaskLimits(null,false, minCrawlDataSizeKb);
        }

        // We'll only permit refresh crawls if the file is small enough
        boolean isRefreshable = size < maxRefreshableCrawlDataSizeKBytes;

        // We'll truncate this down to maxRefreshableCrawlDataSizeKBytes to ensure
        // it's possible to acquire the RAM semaphore
        int effectiveSize = (int) Math.min(maxRefreshableCrawlDataSizeKBytes, size);

        return new CrawlTaskLimits(fileName, isRefreshable, effectiveSize);
    }


    public void acquire(CrawlTaskLimits properties) throws InterruptedException {
        // It's very important that we acquire the RAM semaphore first to avoid a deadlock
        taskSemRAM.acquire(properties.taskSize);
        taskSemCount.acquire(1);
    }

    public void release(CrawlTaskLimits properties) {
        taskSemCount.release(1);
        taskSemRAM.release(properties.taskSize);
    }
}
