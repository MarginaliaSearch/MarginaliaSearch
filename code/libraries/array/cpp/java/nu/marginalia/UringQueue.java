package nu.marginalia;

import java.lang.foreign.MemorySegment;
import java.util.List;

public record UringQueue(MemorySegment pointer, int fd) {
    public static UringQueue open(int fd, int size) {
        return NativeAlgos.uringOpen(fd, size);
    }

    public int read(List<MemorySegment> dest, List<Long> offsets, boolean direct) {
        synchronized (this) {
            return NativeAlgos.uringRead(fd, this, dest, offsets, direct);
        }
    }

    public void close() {
        NativeAlgos.uringClose(this);
    }

}
