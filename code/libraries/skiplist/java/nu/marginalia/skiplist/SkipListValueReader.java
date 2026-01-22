package nu.marginalia.skiplist;

import nu.marginalia.ffi.LinuxSystemCalls;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

public class SkipListValueReader implements AutoCloseable {
    private final int fd;

    public SkipListValueReader(Path filename) {
        fd = LinuxSystemCalls.openBuffered(filename);

        LinuxSystemCalls.fadviseRandom(fd);
    }

    public void read(MemorySegment dest, long offset) throws IOException {
        assert dest.address() != 0;

        if (dest.byteSize() != LinuxSystemCalls.readAt(fd, dest, offset)) {
            throw new IOException("Failed to read values at offset " + offset + ", size " + dest.byteSize());
        }
    }

    public void close() {
        LinuxSystemCalls.closeFd(fd);
    }
 }
