package nu.marginalia.array;

import nu.marginalia.NativeAlgos;

import java.io.IOException;
import java.nio.file.Path;

public class DirectFileReader implements AutoCloseable {
    int fd;

    public DirectFileReader(Path filename) throws IOException {
        fd = NativeAlgos.openDirect(filename);
        if (fd < 0) {
            throw new IOException("Error opening direct file: " + filename);
        }
    }

    public void read(LongArray dest, long offset) throws IOException {
        var segment = dest.getMemorySegment();
        if (NativeAlgos.readAt(fd, segment, offset) != segment.byteSize()) {
            throw new IOException("Failed to read data at " + offset);
        }
    }

    public void close() {
        NativeAlgos.closeFd(fd);
    }
}
