package nu.marginalia.index.positions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

/** Reads positions data from the positions file */
public class PositionsFileReader implements AutoCloseable {

    // We use multiple file channels to avoid reads becoming serialized by the kernel.
    // If we don't do this, multi-threaded reads become strictly slower than single-threaded reads
    // (which is why AsynchronousFileChannel sucks).

    // This is likely the best option apart from O_DIRECT or FFI:ing in libaio or io_uring.

    private final FileChannel[] positions;
    private final ForkJoinPool forkJoinPool;
    private static final Logger logger = LoggerFactory.getLogger(PositionsFileReader.class);

    public PositionsFileReader(Path positionsFile) throws IOException {
        this(positionsFile, 8);
    }

    public PositionsFileReader(Path positionsFile, int nreaders) throws IOException {
        positions = new FileChannel[nreaders];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = FileChannel.open(positionsFile, StandardOpenOption.READ);
        }
        forkJoinPool = new ForkJoinPool(nreaders);
    }

    @Override
    public void close() throws IOException {
        for (FileChannel fc : positions) {
            fc.close();
        }
        forkJoinPool.close();
    }

    /** Get the positions for a keywords in the index, as pointed out by the encoded offsets;
     * intermediate buffers are allocated from the provided arena allocator. */
    public TermData[] getTermData(Arena arena, long[] offsets) {
        TermData[] ret = new TermData[offsets.length];

        int tasks = 0;
        for (long l : offsets) if (l != 0) tasks++;

        CountDownLatch cl = new CountDownLatch(tasks);

        for (int i = 0; i < offsets.length; i++) {
            long encodedOffset = offsets[i];
            if (encodedOffset == 0) continue;

            int idx = i;
            int length = PositionCodec.decodeSize(encodedOffset);
            long offset = PositionCodec.decodeOffset(encodedOffset);
            ByteBuffer buffer = arena.allocate(length).asByteBuffer();

            forkJoinPool.execute(() -> {
                try {
                    positions[idx % positions.length].read(buffer, offset);
                    ret[idx] = new TermData(buffer);
                    cl.countDown();
                }
                catch (IOException ex) {
                    logger.error("Failed to read positions file", ex);
                }
            });
        }

        try {
            cl.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return ret;
    }

}
