package nu.marginalia.array.pool;

import nu.marginalia.ffi.IoUring;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.uring.UringQueue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class BufferPool implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BufferPool.class);

    private final MemoryPage[] pages;
    private Thread monitorThread;

    private final long fileSize;
    private final Arena arena;
    private final int fd;
    private final int pageSizeBytes;
    private PoolLru poolLru;

    private final AtomicLong diskReadCount = new AtomicLong();
    private final AtomicLong cacheReadCount = new AtomicLong();
    private final AtomicLong readAheadCount = new AtomicLong();
    private final AtomicLong readAheadSkippedCount = new AtomicLong();

    public static final int MAX_READ_AHEAD = 8;

    private final List<ReadAheadRing> rings =
            new CopyOnWriteArrayList<>();
    private final ThreadLocal<ReadAheadRing> readAheadRing =
            ThreadLocal.withInitial(this::openReadAheadRing);

    private volatile boolean running = true;

    /** Unassociate all buffers with their addresses, ensuring they will not be cacheable */
    public synchronized void reset() throws InterruptedException {
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
        this.fd = LinuxSystemCalls.openDirect(filename);
        this.pageSizeBytes = pageSizeBytes;
        try {
            this.fileSize = Files.size(filename);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.arena = Arena.ofShared();
        this.pages = new MemoryPage[poolSize];

        MemorySegment memoryArea = arena.allocate((long) pageSizeBytes*poolSize, 4096);
        for (int i = 0; i < pages.length; i++) {
            if (Boolean.getBoolean("system.noSunMiscUnsafe")) {
                pages[i] = (MemoryPage) new SegmentMemoryPage(memoryArea.asSlice((long) i*pageSizeBytes, pageSizeBytes), i);
            }
            else {
                pages[i] = (MemoryPage) new UnsafeMemoryPage(memoryArea.asSlice((long) i*pageSizeBytes, pageSizeBytes), i);
            }
        }

        this.poolLru = new PoolLru(pages);
        this.monitorThread = Thread.ofPlatform().start(this::statsThread);
    }

    private void statsThread() {
        if (!Boolean.getBoolean("index.printPoolStats")) {
            return;
        }

        int diskReadOld = 0;
        int cacheReadOld = 0;

        while (running) {
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                logger.info("Sleep interrupted", e);
                break;
            }

            long diskRead = diskReadCount.get();
            long cacheRead = cacheReadCount.get();
            int heldCount = 0;
            for (var page : pages) {
                if (page.isHeld()) {
                    heldCount++;
                }
            }

            if (diskRead != diskReadOld || cacheRead != cacheReadOld) {
                logger.info("[#{}:{}] Disk/Cached: {}/{}, readAhead={} (skipped {}), heldCount={}/{}, fqs={}, rcc={}",
                        hashCode(), pageSizeBytes,
                        diskRead, cacheRead, readAheadCount.get(), readAheadSkippedCount.get(),
                        heldCount, pages.length,
                        poolLru.getFreeQueueSize(), poolLru.getReclaimCycles());
            }
        }
    }

    public void close() {
        running = false;

        try {
            poolLru.stop();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            for (ReadAheadRing ring : rings) {
                ring.close();
            }

            arena.close();

            LinuxSystemCalls.closeFd(fd);

            System.out.println("Disk read count: " + diskReadCount.get());
            System.out.println("Cached read count: " + cacheReadCount.get());

            try {
                monitorThread.interrupt();
                monitorThread.join();
            }
            catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }


    }

    public long getDiskReadCount() {
        return diskReadCount.get();
    }

    public long getCacheReadCount() {
        return cacheReadCount.get();
    }

    public long getReadAheadCount() {
        return readAheadCount.get();
    }

    public long getReadAheadSkippedCount() {
        return readAheadSkippedCount.get();
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
            buffer = read(address);
        }

        return buffer;
    }

    /** Reads and returns the page at address, and optionally reads and prepares up to 'readAheadPages'
     * ahead of the address, left unpinned in the buffer pool.
     * */
    public MemoryPage get(long address, int readAheadPages) {
        MemoryPage buffer = getExistingBufferForReading(address);

        if (buffer != null) {
            return buffer;
        }

        readAheadPages = Math.min(readAheadPages, Math.min(MAX_READ_AHEAD, pages.length / 8));

        if (readAheadPages <= 0 || !IoUring.isAvailable) {
            return read(address);
        }
        else if (poolLru.getFreeQueueSize() < pages.length / 8) {
            // Skip readahead due to pressure on the pool
            readAheadSkippedCount.incrementAndGet();
            return read(address);
        }
        else {
            return readWithReadAhead(address, readAheadPages);
        }
    }

    private MemoryPage read(long address) {
        // If the page is not available, read it from the caller's thread
        if (address + pageSizeBytes > fileSize) {
            throw new RuntimeException("Address " + address + " too large for page size " + pageSizeBytes + " and file size " + fileSize);
        }
        if ((address & 511) != 0) {
            throw new  RuntimeException("Address " + address + " not aligned");
        }
        MemoryPage buffer = acquireFreePage(address);
        poolLru.register(buffer);
        populateBuffer(buffer);

        if (buffer.pinCount().getAndAdd(1 - MemoryPage.WRITE_LOCKED) >= 0) {
            throw new IllegalStateException("Panic! Write lock was not held during write!");
        }
        diskReadCount.incrementAndGet();

        return buffer;
    }

    private MemoryPage readWithReadAhead(long address, int readAhead) {
        if (address + pageSizeBytes > fileSize) {
            throw new RuntimeException("Address " + address + " too large for page size " + pageSizeBytes + " and file size " + fileSize);
        }
        if ((address & 511) != 0) {
            throw new  RuntimeException("Address " + address + " not aligned");
        }

        ReadAheadRing ring = readAheadRing.get();
        MemoryPage[] batch = ring.batch;
        int n = 0;

        batch[n] = acquireFreePage(address);
        poolLru.register(batch[n++]);

        for (int i = 1; i <= readAhead; i++) {
            long next = address + (long) i * pageSizeBytes;
            if (next + pageSizeBytes > fileSize)
                break;

            MemoryPage resident = poolLru.get(next);
            if (resident != null && resident.pageAddress() == next)
                continue;

            batch[n] = acquireFreePage(next);
            poolLru.register(batch[n++]);
        }

        ring.read(batch, n);

        for (int i = 0; i < n; i++) {
            batch[i].dirty(false);
        }

        for (int i = 1; i < n; i++) { // Leave readahead unpinned, could be claimed or overwritten
            batch[i].pinCount().addAndGet(-MemoryPage.WRITE_LOCKED);
        }

        if (batch[0].pinCount().getAndAdd(1 - MemoryPage.WRITE_LOCKED) >= 0) { // Pin requested page
            throw new IllegalStateException("Panic! Write lock was not held during write!");
        }

        diskReadCount.addAndGet(n);
        readAheadCount.addAndGet(n - 1);

        return batch[0];
    }

    private ReadAheadRing openReadAheadRing() {
        ReadAheadRing ring = new ReadAheadRing();
        rings.add(ring);
        return ring;
    }

    private class ReadAheadRing {
        final MemoryPage[] batch = new MemoryPage[MAX_READ_AHEAD + 1];

        private final UringQueue ring = UringQueue.open(fd, 2 * batch.length);
        private final Arena ringArena = Arena.ofShared();
        private final MemorySegment buffers = ringArena.allocate(8L * batch.length, 8);
        private final MemorySegment sizes = ringArena.allocate(4L * batch.length, 8);
        private final MemorySegment offsets = ringArena.allocate(8L * batch.length, 8);

        void read(MemoryPage[] pages, int n) {
            for (int i = 0; i < n; i++) {
                buffers.setAtIndex(ValueLayout.JAVA_LONG, i, pages[i].getMemorySegment().address());
                sizes.setAtIndex(ValueLayout.JAVA_INT, i, pageSizeBytes);
                offsets.setAtIndex(ValueLayout.JAVA_LONG, i, pages[i].pageAddress());
            }

            int ret = IoUring.readBatchRaw(ring, n, buffers.address(), sizes.address(), offsets.address());
            if (ret != n) {
                throw new IllegalStateException("Batch read failed: " + ret + " of " + n);
            }
        }

        void close() {
            ring.close();
            ringArena.close();
        }
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
        LinuxSystemCalls.readAt(fd, buffer.getMemorySegment(), buffer.pageAddress());
        assert buffer.getMemorySegment().get(ValueLayout.JAVA_INT, 0) != 9999;
        buffer.dirty(false);
    }

    private void waitForPageWrite(MemoryPage page) {
        // The writer offers no wakeup signal, so briefly spin for the common
        // case of a nearly finished write, then poll with a bounded park
        for (int iter = 0; iter < 128; iter++) {
            if (!page.dirty()) {
                return;
            }
            Thread.onSpinWait();
        }

        long parkTime = 5_000;
        while (page.dirty()) {
            LockSupport.parkNanos(parkTime);
            parkTime = Math.min(2 * parkTime, 50_000);
        }
    }

}
