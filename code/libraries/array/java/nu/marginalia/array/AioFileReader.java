package nu.marginalia.array;

import nu.marginalia.NativeAlgos;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;

public class AioFileReader implements AutoCloseable {
    int fd;

    public AioFileReader(Path filename, boolean direct) throws IOException {
        if (direct) {
            fd = NativeAlgos.openDirect(filename);
        }
        else {
            fd = NativeAlgos.openBuffered(filename);
        }
        if (fd < 0) {
            throw new IOException("Error opening direct file: " + filename);
        }
    }

    public void read(List<MemorySegment> destinations, List<Long> offsets) {
        if (destinations.size() < 1024) {
            int ret = NativeAlgos.aioRead(fd, destinations, offsets);
            if (ret != offsets.size())
                throw new RuntimeException("Read failed, rv=" + ret);
        }
        else {
            for (int readStart = 0; readStart < destinations.size(); readStart+=1024) {
                int readSize = Math.min(1024, destinations.size() - readStart);
                int ret = NativeAlgos.aioRead(fd, destinations.subList(readStart, readStart + readSize),
                        offsets.subList(readStart, readStart + readSize));
                if (ret != readSize)
                    throw new RuntimeException("Read failed, rv=" + ret);
            }
        }
    }

    public void close() {
        NativeAlgos.closeFd(fd);
    }
}
