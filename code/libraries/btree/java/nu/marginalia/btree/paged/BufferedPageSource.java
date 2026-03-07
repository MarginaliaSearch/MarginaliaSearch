package nu.marginalia.btree.paged;

import nu.marginalia.ffi.LinuxSystemCalls;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Page source using buffered I/O with fadvise(RANDOM).
 * Buffers are GC-managed via Arena.ofAuto() and recycled through a free list.
 */
class BufferedPageSource implements BTreePageSource {
    private final int fd;
    private final int pageSizeBytes;

    private static final int MAX_FREE_BUFFERS = 8;
    private final ConcurrentLinkedQueue<MemorySegment> freeList = new ConcurrentLinkedQueue<>();
    private final AtomicInteger freeCount = new AtomicInteger();

    BufferedPageSource(Path filePath, int pageSizeBytes) {
        this.pageSizeBytes = pageSizeBytes;
        this.fd = LinuxSystemCalls.openBuffered(filePath);
        LinuxSystemCalls.fadviseRandom(fd);
    }

    @Override
    public BTreePage get(long address) {
        MemorySegment buf = acquireBuffer();
        LinuxSystemCalls.readAt(fd, buf, address);
        return new SegmentPage(this, buf, address);
    }

    private MemorySegment acquireBuffer() {
        MemorySegment buf = freeList.poll();
        if (buf != null) {
            freeCount.decrementAndGet();
            return buf;
        }
        return Arena.ofAuto().allocate(pageSizeBytes, 8);
    }

    void releaseBuffer(MemorySegment buf) {
        if (freeCount.get() < MAX_FREE_BUFFERS) {
            freeCount.incrementAndGet();
            freeList.add(buf);
        }
        // Otherwise the buffer becomes unreachable and is GC'd via ofAuto()
    }

    @Override
    public void close() {
        freeList.clear();
        LinuxSystemCalls.closeFd(fd);
    }

    private record SegmentPage(BufferedPageSource source, MemorySegment seg, long address) implements BTreePage {
        @Override
        public int getInt(int offset) {
            return seg.get(ValueLayout.JAVA_INT, offset);
        }

        @Override
        public long getLong(int offset) {
            return seg.get(ValueLayout.JAVA_LONG, offset);
        }

        @Override
        public long pageAddress() {
            return address;
        }

        @Override
        public void close() {
            source.releaseBuffer(seg);
        }
    }
}
