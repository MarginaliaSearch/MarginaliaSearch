package nu.marginalia.array.pool;

import nu.marginalia.NativeAlgos;
import nu.marginalia.array.algo.LongArrayBuffer;
import nu.marginalia.array.page.UnsafeLongArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.foreign.Arena;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BufferPool implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BufferPool.class);

    private final UnsafeLongArrayBuffer[] pages;
    private final Arena arena;
    private final int fd;
    private volatile UnsafeLongArrayBuffer lastAccessedBuffer;
    private PoolLru poolLru;

    final AtomicInteger diskReadCount = new AtomicInteger();
    final AtomicInteger cacheReadCount = new AtomicInteger();

    private volatile boolean running = true;

    /** Unassociate all buffers with their addresses, ensuring they will not be cacheable */
    public synchronized void reset() {
        for (var page : pages) {
            page.pageAddress(-1);
        }
        poolLru = new PoolLru(pages);
    }

    public BufferPool(Path filename, int pageSizeBytes, int poolSize) {
        this.fd = NativeAlgos.openDirect(filename);

        this.arena = Arena.ofShared();
        this.pages = new UnsafeLongArrayBuffer[poolSize];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new UnsafeLongArrayBuffer(arena.allocate(pageSizeBytes, 512));
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

                if (diskRead != diskReadOld || cacheRead != cacheReadOld) {
                    logger.info("[#{}:{}] Disk/Cached: {}/{}", hashCode(), pageSizeBytes, diskRead, cacheRead);
                }
            }
        });
    }

    public void close() throws InterruptedException {
        running = false;

        NativeAlgos.closeFd(fd);
        arena.close();

        System.out.println("Disk read count: " + diskReadCount.get());
        System.out.println("Cached read count: " + cacheReadCount.get());
    }

    @Nullable
    public UnsafeLongArrayBuffer getExistingBufferForReading(long address) {

        // Fast path for checking the last buffer we accessed

        var cachedBuffer = this.lastAccessedBuffer;
        if (cachedBuffer != null && cachedBuffer.pageAddress() == address) {
            if (cachedBuffer.acquireAsReader(address)) {
                cacheReadCount.incrementAndGet();
                return cachedBuffer;
            }
        }

        cachedBuffer = poolLru.get(address);
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

    public UnsafeLongArrayBuffer get(long address) {
        // Look through available pages for the one we're looking for
        UnsafeLongArrayBuffer buffer = getExistingBufferForReading(address);

        if (buffer == null) {
            // If the page is not available, read it from the caller's thread
            buffer = acquireFreePage(address);
            poolLru.register(buffer);
            populateBuffer(buffer);

            // Flip from a write lock to a read lock immediately
            if (!buffer.pinCount().compareAndSet(-1, 1)) {
                throw new IllegalStateException("Panic! Write lock was not held during write!");
            }

            diskReadCount.incrementAndGet();
        }

        lastAccessedBuffer = buffer;

        return buffer;
    }

    private UnsafeLongArrayBuffer acquireFreePage(long address) {
        for (;;) {
            var free = poolLru.getFree();
            if (free != null && free.acquireForWriting(address)) {
                return free;
            }
        }
    }

    private void populateBuffer(LongArrayBuffer buffer) {
        NativeAlgos.readAt(fd, buffer.getMemorySegment(), buffer.pageAddress());
        buffer.dirty(false);
    }

    private void waitForPageWrite(LongArrayBuffer page) {
        if (!page.dirty()) {
            return;
        }

        synchronized (page) {
            while (page.dirty()) {
                try {
                    page.wait(1);
                }
                catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }


}
