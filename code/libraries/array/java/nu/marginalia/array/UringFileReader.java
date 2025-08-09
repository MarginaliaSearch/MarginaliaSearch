package nu.marginalia.array;

import nu.marginalia.NativeAlgos;
import nu.marginalia.UringQueue;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class UringFileReader implements AutoCloseable {
    private final UringQueue[] rings = new UringQueue[8];
    private final AtomicLong ringIdx = new AtomicLong();
    private final int fd;
    private final boolean direct;

    private static final int QUEUE_SIZE = 8192;

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
            rings[i] = UringQueue.open(fd, QUEUE_SIZE);
        }
        if (fd < 0) {
            throw new IOException("Error opening direct file: " + filename);
        }
    }

    public void fadviseWillneed() {
        NativeAlgos.fadviseWillneed(fd);
    }

    public void read(List<MemorySegment> destinations, List<Long> offsets) {
        if (destinations.size() < 5) {
            for (int  i = 0; i < destinations.size(); i++) {
                var ms = destinations.get(i);
                long offset = offsets.get(i);

                int ret;
                if (ms.byteSize() != (ret = NativeAlgos.readAt(fd, ms, offset))) {
                    throw new RuntimeException("Read failed, rv=" + ret);
                }
            }
            return;
        }
        var ring = rings[(int) (ringIdx.getAndIncrement() % rings.length)];

        if (destinations.size() <= QUEUE_SIZE) {
            int ret = ring.read(destinations, offsets, direct);
            if (ret != offsets.size()) {
                throw new RuntimeException("Read failed, rv=" + ret);
            }
        }
        else {
            // We *could* break the task into multiple submissions, but this leads to some
            // very unpredictable read latencies
            throw new IllegalArgumentException("Submission size exceeds queue size!");
        }
    }

    public void close() {
        for (var ring : rings) {
            ring.close();
        }
        NativeAlgos.closeFd(fd);
    }
}
