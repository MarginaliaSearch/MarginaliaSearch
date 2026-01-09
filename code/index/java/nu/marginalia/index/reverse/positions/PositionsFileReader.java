package nu.marginalia.index.reverse.positions;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.asyncio.AsyncReadRequest;
import nu.marginalia.asyncio.UringExecutionQueue;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.ffi.NativeAlgos;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.VarintCodedSequence;
import nu.marginalia.uring.UringFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/** Reads positions data from the positions file */
public class PositionsFileReader implements AutoCloseable {

    private final UringExecutionQueue executionQueue;
    private final int fileDescriptor;
    private static final Logger logger = LoggerFactory.getLogger(PositionsFileReader.class);

    public PositionsFileReader(Path positionsFile) throws IOException {
        fileDescriptor = LinuxSystemCalls.openBuffered(positionsFile);
        executionQueue = new UringExecutionQueue(16);
    }

    @Override
    public void close() throws IOException {
        try {
            executionQueue.close();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            LinuxSystemCalls.closeFd(fileDescriptor);
        }
    }

    /** Get the positions for a keywords in the index, as pointed out by the encoded offsets;
     * intermediate buffers are allocated from the provided arena allocator. */
    public CompletableFuture<IntList[]> getTermData(long[] offsets) throws InterruptedException {

        MemorySegment[] segments = new MemorySegment[offsets.length];
        List<AsyncReadRequest> requests = new ArrayList<>(offsets.length);
        for (int i = 0; i < offsets.length; i++) {
            long encodedOffset = offsets[i];
            if (encodedOffset == 0) continue;

            int size = PositionCodec.decodeSize(encodedOffset);
            long offest = PositionCodec.decodeOffset(encodedOffset);

            var segment = Arena.ofAuto().allocate(size, 8);
            segments[i] = segment;

            requests.add(new AsyncReadRequest(fileDescriptor, segment, offest));
        }

        System.out.println("A: " + requests.size());
        return executionQueue.submit(segments, requests).thenApply(
                seg -> {
                    System.out.println("B");
                    IntList[] ret = new IntList[seg.length];
                    for (int i = 0; i < seg.length; i++) {
                        if (seg[i] != null) {
                            ByteBuffer buffer = seg[i].asByteBuffer();
                            ret[i] = new VarintCodedSequence(buffer, 0, buffer.capacity()).values();
                        }
                    }
                    return ret;
                }
        );
    }

}
