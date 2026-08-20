package nu.marginalia.array;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class LongArrayFileWriter implements AutoCloseable {
    private static final int BUFFER_SIZE_LONGS = 128 * 1024;
    private static final long MAX_CHUNK_BYTES = 1L << 30;

    private final FileChannel channel;
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment buffer;
    private final ByteBuffer bufferView;

    private int bufferPos = 0;
    private boolean closed;

    public static LongArrayFileWriter create(Path file) throws IOException {
        return new LongArrayFileWriter(FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING));
    }

    private LongArrayFileWriter(FileChannel channel) {
        this.channel = channel;
        this.buffer = arena.allocate(JAVA_LONG.byteSize() * BUFFER_SIZE_LONGS, 16);
        this.bufferView = buffer.asByteBuffer();
    }

    public void put(long value) throws IOException {
        if (bufferPos == BUFFER_SIZE_LONGS) {
            flush();
        }
        buffer.setAtIndex(JAVA_LONG, bufferPos++, value);
    }

    public void put(LongArray source, long start, long end) throws IOException {
        long length = end - start;

        if (length < 0)
            throw new IllegalArgumentException("Negative range");
        if (end > source.size())
            throw new IndexOutOfBoundsException("Range exceeds source array");

        MemorySegment sourceSegment = source.getMemorySegment();

        // Large ranges of native memory are written straight from the source, anything else goes via the buffer
        if (length > BUFFER_SIZE_LONGS && sourceSegment.isNative()) {
            flush();
            writeSegment(sourceSegment.asSlice(start * JAVA_LONG.byteSize(), length * JAVA_LONG.byteSize()));
            return;
        }

        while (length > 0) {
            if (bufferPos == BUFFER_SIZE_LONGS) {
                flush();
            }

            int chunk = (int) Math.min(length, BUFFER_SIZE_LONGS - bufferPos);
            MemorySegment.copy(sourceSegment, JAVA_LONG, start * JAVA_LONG.byteSize(),
                    buffer, JAVA_LONG, bufferPos * JAVA_LONG.byteSize(),
                    chunk);

            bufferPos += chunk;
            start += chunk;
            length -= chunk;
        }
    }

    private void flush() throws IOException {
        if (bufferPos == 0)
            return;

        bufferView.position(0);
        bufferView.limit((int) (bufferPos * JAVA_LONG.byteSize()));
        while (bufferView.hasRemaining()) {
            channel.write(bufferView);
        }

        bufferPos = 0;
    }

    /** Flush and request that the file be persisted to storage */
    public void force() throws IOException {
        flush();
        channel.force(false);
    }

    private void writeSegment(MemorySegment segment) throws IOException {
        for (long offset = 0; offset < segment.byteSize(); offset += MAX_CHUNK_BYTES) {
            long length = Math.min(MAX_CHUNK_BYTES, segment.byteSize() - offset);
            ByteBuffer chunk = segment.asSlice(offset, length).asByteBuffer();
            while (chunk.hasRemaining()) {
                channel.write(chunk);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (closed)
            return;
        closed = true;

        try {
            flush();
        }
        finally {
            arena.close();
            channel.close();
        }
    }
}
