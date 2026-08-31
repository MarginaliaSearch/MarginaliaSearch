package nu.marginalia.array.pool;

import nu.marginalia.ffi.LinuxSystemCalls;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class UncachedBufferPool implements PagePool {
    private final Arena arena;
    private final int fd;
    private final long fileSize;
    private final int pageSizeBytes;

    private final ArrayBlockingQueue<MemoryPage> freePages;

    private final AtomicLong diskReadCount = new AtomicLong();
    private final AtomicLong readAheadCount = new AtomicLong();

    public UncachedBufferPool(Path filename, int pageSizeBytes, int poolSize) {
        this.fd = LinuxSystemCalls.openBuffered(filename);
        this.pageSizeBytes = pageSizeBytes;

        try {
            this.fileSize = Files.size(filename);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.arena = Arena.ofShared();
        this.freePages = new ArrayBlockingQueue<>(poolSize);

        LinuxSystemCalls.fadviseRandom(this.fd);

        MemorySegment memoryArea = arena.allocate((long) pageSizeBytes * poolSize, 4096);
        boolean noUnsafe = Boolean.getBoolean("system.noSunMiscUnsafe");
        for (int i = 0; i < poolSize; i++) {
            MemorySegment slice = memoryArea.asSlice((long) i * pageSizeBytes, pageSizeBytes);
            if (noUnsafe) {
                freePages.add(new PooledSegmentPage(slice, i));
            }
            else {
                freePages.add(new PooledUnsafePage(slice, i));
            }
        }
    }

    public long getDiskReadCount() {
        return diskReadCount.get();
    }

    public long getReadAheadCount() {
        return readAheadCount.get();
    }

    @Override
    public MemoryPage get(long address) {
        if (address + pageSizeBytes > fileSize) {
            throw new IllegalArgumentException("Address " + address + " too large for page size " + pageSizeBytes + " and file size " + fileSize);
        }

        MemoryPage page;
        try {
            page = freePages.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        page.pageAddress(address);
        page.pinCount().set(1);

        try {
            int read = LinuxSystemCalls.readAt(fd, page.getMemorySegment(), address);
            if (read != pageSizeBytes) {
                throw new IllegalStateException("Read returned " + read + " of " + pageSizeBytes + " bytes at " + address);
            }
        } catch (Throwable t) {
            page.pinCount().set(0);
            freePages.offer(page);
            throw t;
        }

        diskReadCount.incrementAndGet();

        return page;
    }

    @Override
    public MemoryPage get(long address, int readAheadPages) {
        if (readAheadPages > 0) {
            long readAheadStart = address + pageSizeBytes;
            long readAheadEnd = Math.min(fileSize, readAheadStart + (long) readAheadPages * pageSizeBytes);

            if (readAheadEnd > readAheadStart) {
                LinuxSystemCalls.fadviseWillneed(fd, readAheadStart, readAheadEnd - readAheadStart);
                readAheadCount.addAndGet((readAheadEnd - readAheadStart) / pageSizeBytes);
            }
        }

        return get(address);
    }

    /** No-op since no data is retained between get() calls */
    @Override
    public void reset() {
    }

    @Override
    public void close() {
        arena.close();
        LinuxSystemCalls.closeFd(fd);
    }

    private class PooledUnsafePage extends UnsafeMemoryPage {
        private PooledUnsafePage(MemorySegment segment, int ord) {
            super(segment, ord);
        }

        @Override
        public void close() {
            if (pinCount().decrementAndGet() == 0) {
                freePages.offer(this);
            }
        }
    }

    private class PooledSegmentPage extends SegmentMemoryPage {
        private PooledSegmentPage(MemorySegment segment, int ord) {
            super(segment, ord);
        }

        @Override
        public void close() {
            if (pinCount().decrementAndGet() == 0) {
                freePages.offer(this);
            }
        }
    }
}
