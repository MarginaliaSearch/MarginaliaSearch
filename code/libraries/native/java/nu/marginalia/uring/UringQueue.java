package nu.marginalia.uring;

import nu.marginalia.ffi.IoUring;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** A registered-file io_uring, obtained via {@link IoUring#uringOpen}.  Reads are
 *  submitted in batches through the raw entry points in {@link IoUring}.  These
 *  provide no synchronization, so a ring must only be used by one thread at a time.
 */
public final class UringQueue {
    private final MemorySegment pointer;
    private final int fd;

    public UringQueue(MemorySegment pointer, int fd) {
        this.pointer = pointer;
        this.fd = fd;
    }

    public static UringQueue open(int fd, int size) {
        return IoUring.uringOpen(fd, size);
    }

    public void close() {
        IoUring.uringClose(this);
    }

    public MemorySegment pointer() {
        return pointer;
    }

    public int fd() {
        return fd;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (UringQueue) obj;
        return Objects.equals(this.pointer, that.pointer) &&
                this.fd == that.fd;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pointer, fd);
    }
}
