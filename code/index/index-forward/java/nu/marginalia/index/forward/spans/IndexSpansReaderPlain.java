package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

public class IndexSpansReaderPlain implements IndexSpansReader {
    private final FileChannel[] spansFileChannels;
    private final ForkJoinPool forkJoinPool;

    public IndexSpansReaderPlain(Path spansFile) throws IOException {
        this.spansFileChannels = new FileChannel[8];
        for (int i = 0; i < spansFileChannels.length; i++) {
            spansFileChannels[i] = (FileChannel) Files.newByteChannel(spansFile, StandardOpenOption.READ);
        }
        forkJoinPool = new ForkJoinPool(spansFileChannels.length);
    }

    @Override
    public DocumentSpans readSpans(Arena arena, long encodedOffset) throws IOException {
        // Decode the size and offset from the encoded offset
        long size = SpansCodec.decodeSize(encodedOffset);
        long offset = SpansCodec.decodeStartOffset(encodedOffset);

        var ms = arena.allocate(size, 4);
        // Allocate a buffer from the arena
        var buffer = ms.asByteBuffer();
        while (buffer.hasRemaining()) {
            spansFileChannels[0].read(buffer, offset + buffer.position());
        }

        return decode(ms);
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
    public DocumentSpans[] readSpans(Arena arena, long[] encodedOffsets) throws IOException {
        int numJobs = 0;
        for (long offset : encodedOffsets) {
            if (offset < 0)
                continue;
            numJobs++;
        }

        DocumentSpans[] ret = new DocumentSpans[encodedOffsets.length];
        if (numJobs == 0) return ret;

        CountDownLatch latch = new CountDownLatch(numJobs);

        for (int idx = 0; idx < encodedOffsets.length; idx++) {
            if (encodedOffsets[idx] < 0)
                continue;
            long size = SpansCodec.decodeSize(encodedOffsets[idx]);
            long start = SpansCodec.decodeStartOffset(encodedOffsets[idx]);

            int i = idx;
            forkJoinPool.execute(() -> {
                try {
                    MemorySegment slice = arena.allocate(size);
                    var buffer = slice.asByteBuffer();
                    spansFileChannels[i% spansFileChannels.length].read(buffer, start);
                    ret[i] = decode(slice);
                }
                catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                finally {
                    latch.countDown();
                }
            });
        }
        try {
            do {
                latch.await();
            }
            while (latch.getCount() != 0);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return ret;
    }

    @Override
    public void close() throws IOException {
        for (var spansFileChannel : spansFileChannels) {
            spansFileChannel.close();
        }
    }

}
