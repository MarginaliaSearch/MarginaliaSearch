package nu.marginalia.array.pool;

import nu.marginalia.NativeAlgos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BufferPool implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BufferPool.class);

    private final MemoryPage[] pages;

    private final Arena arena;
    private final int fd;
    private PoolLru poolLru;

    private final AtomicInteger diskReadCount = new AtomicInteger();
    private final AtomicInteger cacheReadCount = new AtomicInteger();

    private volatile boolean running = true;

    /** Unassociate all buffers with their addresses, ensuring they will not be cacheable */
    public synchronized void reset() {
        for (var page : pages) {
            page.pageAddress(-1);
        }
        try {
            poolLru.stop();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        poolLru = new PoolLru(pages);
    }

    public BufferPool(Path filename, int pageSizeBytes, int poolSize) {
        this.fd = NativeAlgos.openDirect(filename);

        this.arena = Arena.ofShared();
        this.pages = new UnsafeMemoryPage[poolSize];
        for (int i = 0; i < pages.length; i++) {
            if (Boolean.getBoolean("system.noSunMiscUnsafe")) {
                pages[i] = new SegmentMemoryPage(arena.allocate(pageSizeBytes, 512), i);
            }
            else {
                pages[i] = new UnsafeMemoryPage(arena.allocate(pageSizeBytes, 512), i);
            }
        }

        this.poolLru = new PoolLru(pages);

        Thread.ofPlatform().start(() -> {
            int diskReadOld = 0;
            int cacheReadOld = 0;

            while (running) {
                try {
                    TimeUnit.SECONDS.sleep(30);
                } catch (InterruptedException e) {
                    logger.info("Sleep interrupted", e);
                    break;
                }

                int diskRead = diskReadCount.get();
                int cacheRead = cacheReadCount.get();
                int heldCount = 0;
                for (var page : pages) {
                    if (page.isHeld()) {
                        heldCount++;
                    }
                }

                if (diskRead != diskReadOld || cacheRead != cacheReadOld) {
                    logger.info("[#{}:{}] Disk/Cached: {}/{}, heldCount={}/{}, fqs={}, rcc={}", hashCode(), pageSizeBytes, diskRead, cacheRead, heldCount, pages.length, poolLru.getFreeQueueSize(), poolLru.getReclaimCycles());
                }
            }
        });
    }

    public void close() {
        running = false;

        NativeAlgos.closeFd(fd);
        arena.close();

        System.out.println("Disk read count: " + diskReadCount.get());
        System.out.println("Cached read count: " + cacheReadCount.get());
    }

    @Nullable
    public MemoryPage getExistingBufferForReading(long address) {
        MemoryPage cachedBuffer = poolLru.get(address);
        if (cachedBuffer != null && cachedBuffer.pageAddress() == address) {

            // Try to acquire the page normally
            if (cachedBuffer.acquireAsReader(address)) {
                cacheReadCount.incrementAndGet();

                return cachedBuffer;
            }

            if (cachedBuffer.pageAddress() != address)
                return null;

            // The page we are looking for is currently being written
            waitForPageWrite(cachedBuffer);

            if (cachedBuffer.acquireAsReader(address)) {
                this.cacheReadCount.incrementAndGet();
                return cachedBuffer;
            }
        }

        return null;
    }

    public MemoryPage get(long address) {
        // Look through available pages for the one we're looking for
        MemoryPage buffer = getExistingBufferForReading(address);

        if (buffer == null) {
            buffer = read(address, true);
        }

        return buffer;
    }

    private MemoryPage read(long address, boolean acquire) {
        // If the page is not available, read it from the caller's thread
        MemoryPage buffer = acquireFreePage(address);
        poolLru.register(buffer);
        populateBuffer(buffer);

        if (acquire) {
            if (!buffer.pinCount().compareAndSet(-1, 1)) {
                throw new IllegalStateException("Panic! Write lock was not held during write!");
            }
        }
        else {
            if (!buffer.pinCount().compareAndSet(-1, 0)) {
                throw new IllegalStateException("Panic! Write lock was not held during write!");
            }
        }

        diskReadCount.incrementAndGet();

        return buffer;
    }

    private MemoryPage acquireFreePage(long address) {
        for (;;) {
            var free = poolLru.getFree();
            if (free != null && free.acquireForWriting(address)) {
                return free;
            }
        }
    }

    private void populateBuffer(MemoryPage buffer) {
        if (getClass().desiredAssertionStatus()) {
            buffer.getMemorySegment().set(ValueLayout.JAVA_INT, 0, 9999);
        }
        NativeAlgos.readAt(fd, buffer.getMemorySegment(), buffer.pageAddress());
        assert buffer.getMemorySegment().get(ValueLayout.JAVA_INT, 0) != 9999;
        buffer.dirty(false);

        if (buffer.pinCount().get() > 1) {
            synchronized (buffer) {
                buffer.notifyAll();
            }
        }
    }

    private void waitForPageWrite(MemoryPage page) {
        if (!page.dirty()) {
            return;
        }

        synchronized (page) {
            while (page.dirty()) {
                try {
                    page.wait(0, 1000);
                }
                catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }


}
