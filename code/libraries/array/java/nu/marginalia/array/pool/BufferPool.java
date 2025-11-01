package nu.marginalia.array.pool;

import nu.marginalia.ffi.LinuxSystemCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BufferPool implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BufferPool.class);

    private final MemoryPage[] pages;
    private Thread monitorThread;

    private final long fileSize;
    private final Arena arena;
    private final int fd;
    private final int pageSizeBytes;
    private PoolLru poolLru;

    private final AtomicInteger diskReadCount = new AtomicInteger();
    private final AtomicInteger cacheReadCount = new AtomicInteger();
    private final AtomicInteger prefetchReadCount = new AtomicInteger();

    private final Prefetcher prefetcher;

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
        prefetcher.reset();
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

        monitorThread = Thread.ofPlatform().start(() -> {
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
                int prefetchRead = prefetchReadCount.get();
                int cacheRead = cacheReadCount.get();
                int heldCount = 0;
                for (var page : pages) {
                    if (page.isHeld()) {
                        heldCount++;
                    }
                }

                if (diskRead != diskReadOld || cacheRead != cacheReadOld) {
                    logger.info("[#{}:{}] Disk/Prefetched/Cached: {}/{}/{}, heldCount={}/{}, fqs={}, rcc={}",
                            hashCode(), pageSizeBytes,
                            diskRead, prefetchRead, cacheRead,
                            heldCount, pages.length,
                            poolLru.getFreeQueueSize(), poolLru.getReclaimCycles());
                }
            }
        });

        this.prefetcher = new Prefetcher(Math.max(1, 128*1024/pageSizeBytes));
    }

    public void close() {
        running = false;

        try {
            poolLru.stop();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        LinuxSystemCalls.closeFd(fd);
        arena.close();

        System.out.println("Disk read count: " + diskReadCount.get());
        System.out.println("Cached read count: " + cacheReadCount.get());

        try {
            monitorThread.interrupt();
            monitorThread.join();

            prefetcher.stop();
        }
        catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

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

    public void prefetch(long address) {
        prefetcher.requestPrefetch(address);
    }

    private void prefetchNow(long address) {
        // Look through available pages for the one we're looking for
        MemoryPage buffer = poolLru.get(address);

        if (buffer != null) // already cached
            return;

        // buffer is read unacquired, no need to close the return value
        read(address, false);

    }


    private MemoryPage read(long address, boolean acquire) {
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

        if (acquire) {
            if (!buffer.pinCount().compareAndSet(-1, 1)) {
                throw new IllegalStateException("Panic! Write lock was not held during write!");
            }
            diskReadCount.incrementAndGet();
        }
        else {
            if (!buffer.pinCount().compareAndSet(-1, 0)) {
                throw new IllegalStateException("Panic! Write lock was not held during write!");
            }
            prefetchReadCount.incrementAndGet();
        }


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
        LinuxSystemCalls.readAt(fd, buffer.getMemorySegment(), buffer.pageAddress());
        assert buffer.getMemorySegment().get(ValueLayout.JAVA_INT, 0) != 9999;
        buffer.dirty(false);

        synchronized (buffer) {
            buffer.notifyAll();
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

    class Prefetcher {
        private final List<Thread> threads;
        private final int nThreads;
        private final ArrayBlockingQueue<Long> prefetchQueue;

        public Prefetcher(int nThreads) {
            this.threads = new ArrayList<>(nThreads);
            this.prefetchQueue = new ArrayBlockingQueue<>(4*nThreads);
            this.nThreads = nThreads;

            start(nThreads);
        }

        public void reset() throws InterruptedException {
            stop();
            start(nThreads);
        }

        public void stop() throws InterruptedException {
            var iter = threads.iterator();
            while (iter.hasNext()) {
                var thread = iter.next();
                thread.interrupt();
                thread.join();
                iter.remove();
            }
        }

        private void start(int n) {
            for (int i = 0; i < n; i++) {
                threads.add(Thread.ofPlatform().name("BufferPool:Prefetcher").daemon().start(this::prefetchThread));
            }
        }

        private void prefetchThread() {
            try {
                for (;;) {
                    Long address = prefetchQueue.poll(1, TimeUnit.SECONDS);
                    if (null == address) {
                        continue;
                    }
                    prefetchNow(address);
                }
            }
            catch (InterruptedException ex) {
                //
            }
        }

        public void requestPrefetch(long address) {
            prefetchQueue.offer(address);
        }
    }

}
