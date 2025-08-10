package nu.marginalia.uring;

import nu.marginalia.ffi.IoUring;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class UringQueue {
    private final MemorySegment pointer;
    private final int fd;
    private final Lock  lock = new ReentrantLock(true);

    public UringQueue(MemorySegment pointer, int fd) {
        this.pointer = pointer;
        this.fd = fd;
    }

    public static UringQueue open(int fd, int size) {
        return IoUring.uringOpen(fd, size);
    }

    public int read(List<MemorySegment> dest, List<Long> offsets, boolean direct) {
        try {
            if (!lock.tryLock(10, TimeUnit.MILLISECONDS))
                throw new RuntimeException("io_uring slow, likely backpressure!");

            try {
                return IoUring.uringReadBatch(fd, this, dest, offsets, direct);
            }
            finally {
                lock.unlock();
            }
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
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

    @Override
    public String toString() {
        return "UringQueue[" +
                "pointer=" + pointer + ", " +
                "fd=" + fd + ']';
    }


}
