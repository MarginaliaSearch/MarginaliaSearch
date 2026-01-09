package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import nu.marginalia.asyncio.AsyncReadRequest;
import nu.marginalia.asyncio.UringExecutionQueue;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.ffi.NativeAlgos;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.uring.UringFileReader;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public class IndexSpansReaderPlain implements IndexSpansReader {
    private final UringExecutionQueue executionQueue;
    private int fileDescriptor;
    public IndexSpansReaderPlain(Path spansFile) throws IOException {
        executionQueue = new UringExecutionQueue(16);
        fileDescriptor = LinuxSystemCalls.openBuffered(spansFile);
        LinuxSystemCalls.fadviseWillneed(fileDescriptor);
    }

    @Override
    public DecodableDocumentSpans readSpan(Arena arena, long encodedOffset) {

        if (encodedOffset < 0) {
            return null;
        }
        long offset = SpansCodec.decodeStartOffset(encodedOffset);
        int size = SpansCodec.decodeSize(encodedOffset);

        MemorySegment segment = arena.allocate(size, 8);

        LinuxSystemCalls.readAt(fileDescriptor, segment, offset);

        return new DecodableDocumentSpans(segment);
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

}
