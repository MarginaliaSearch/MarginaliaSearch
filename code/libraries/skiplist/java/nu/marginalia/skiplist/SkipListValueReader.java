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

    /** Create a context for reading value blocks in batches.  A context holds a
     *  ring on this reader's descriptor, so it must be closed before the reader
     *  is, and may only be used by one thread at a time. */
    public ValueBatchContext createBatchContext() {
        return new ValueBatchContext(this, fd);
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
