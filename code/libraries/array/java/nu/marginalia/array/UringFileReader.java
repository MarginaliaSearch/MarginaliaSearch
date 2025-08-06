package nu.marginalia.array;

import nu.marginalia.NativeAlgos;
import nu.marginalia.UringQueue;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class UringFileReader implements AutoCloseable {
    private final UringQueue[] rings = new UringQueue[5];
    private final AtomicLong ringIdx = new AtomicLong();
    private final int fd;
    private final boolean direct;

    public UringFileReader(Path filename, boolean direct) throws IOException {


        if (direct) {
            fd = NativeAlgos.openDirect(filename);
            this.direct = true;
        }
        else {
            fd = NativeAlgos.openBuffered(filename);
            NativeAlgos.fadviseRandom(fd);
            this.direct = false;
        }
        for (int i = 0; i < rings.length; i++) {
            rings[i] = UringQueue.open(fd, 1024);
        }
        if (fd < 0) {
            throw new IOException("Error opening direct file: " + filename);
        }
    }

    public void read(List<MemorySegment> destinations, List<Long> offsets) {
        var ring = rings[(int) (ringIdx.getAndIncrement() % rings.length)];

        if (destinations.size() < 1024) {
            int ret = ring.read(destinations, offsets, direct);
            if (ret != offsets.size()) {
                throw new RuntimeException("Read failed, rv=" + ret);
            }
        }
        else {
            for (int readStart = 0; readStart < destinations.size(); readStart+=1024) {
                int readSize = Math.min(1024, destinations.size() - readStart);
                int ret = ring.read(destinations.subList(readStart, readStart + readSize), offsets.subList(readStart, readStart + readSize), direct);
                if (ret != readSize)
                    throw new RuntimeException("Read failed, rv=" + ret);
            }
        }
    }

    public void close() {
        for (var ring : rings) {
            ring.close();
        }
        NativeAlgos.closeFd(fd);
    }
}
