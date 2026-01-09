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
    public CompletableFuture<DocumentSpans> readSpan(Arena arena, long encodedOffset) throws InterruptedException {

        if (encodedOffset < 0) {
            return CompletableFuture.completedFuture(new DocumentSpans());
        }
        long offset = SpansCodec.decodeStartOffset(encodedOffset);
        int size = SpansCodec.decodeSize(encodedOffset);

        MemorySegment segment = arena.allocate(size, 8);

        return executionQueue
                .submit(segment, new AsyncReadRequest(fileDescriptor, segment, offset))
                .thenApply(this::decode);
    }

    public DocumentSpans decode(MemorySegment ms) {
        int count = ms.get(ValueLayout.JAVA_INT, 0);
        int pos = 4;
        DocumentSpans ret = new DocumentSpans();

        // Decode each span
        for (int spanIdx = 0; spanIdx < count; spanIdx++) {
            byte code = ms.get(ValueLayout.JAVA_BYTE, pos);
            short len = ms.get(ValueLayout.JAVA_SHORT, pos+2);

            IntArrayList values = new IntArrayList(len);

            pos += 4;
            for (int i = 0; i < len; i++) {
                values.add(ms.get(ValueLayout.JAVA_INT, pos + 4*i));
            }
            ret.accept(code, values);
            pos += 4*len;
        }

        return ret;
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
