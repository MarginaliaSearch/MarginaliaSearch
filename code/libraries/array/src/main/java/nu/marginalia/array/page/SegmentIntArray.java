package nu.marginalia.array.page;

import nu.marginalia.array.ArrayRangeReference;
import nu.marginalia.array.IntArray;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public class SegmentIntArray implements PartitionPage, IntArray {

    @Nullable
    private final Arena arena;
    private final MemorySegment segment;
    private boolean closed;

    SegmentIntArray(MemorySegment segment,
                    @Nullable Arena arena) {
        this.segment = segment;
        this.arena = arena;
    }

    public static SegmentIntArray onHeap(Arena arena, long size) {
        return new SegmentIntArray(arena.allocate(WORD_SIZE*size, 8), arena);
    }

    public static SegmentIntArray fromMmapReadOnly(Arena arena, Path file, long offset, long size) throws IOException {
        return new SegmentIntArray(
                mmapFile(arena, file, offset, size, FileChannel.MapMode.READ_ONLY, StandardOpenOption.READ),
                arena);
    }

    public static SegmentIntArray fromMmapReadWrite(Arena arena, Path file, long offset, long size) throws IOException {

        return new SegmentIntArray(
                mmapFile(arena, file, offset, size, FileChannel.MapMode.READ_WRITE,
                        StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE),
                arena);
    }

    private static MemorySegment mmapFile(Arena arena,
                                       Path file,
                                       long offset,
                                       long size,
                                       FileChannel.MapMode mode,
                                       OpenOption... openOptions) throws IOException
    {
        try (var channel = (FileChannel) Files.newByteChannel(file, openOptions)) {

            return channel.map(mode,
                            JAVA_INT.byteSize() * offset,
                            JAVA_INT.byteSize() * size,
                            arena);
        }
        catch (IOException ex) {
            throw new IOException("Failed to map file " + file + " (" + offset + ":" + size + ")", ex);
        }
    }

    @Override
    public IntArray range(long start, long end) {
        return new SegmentIntArray(
            segment.asSlice(
                start * JAVA_INT.byteSize(),
                (end-start) * JAVA_INT.byteSize()),
            null);
    }

    @Override
    public IntArray shifted(long start) {
        return new SegmentIntArray(
                segment.asSlice(start * JAVA_INT.byteSize()),
            null);
    }

    @Override
    public int get(long at) {
        try {
            return segment.getAtIndex(JAVA_INT, at);
        }
        catch (IndexOutOfBoundsException ex) {
            throw new IndexOutOfBoundsException("@" + at + "(" + 0 + ":" + segment.byteSize()/8 + ")");
        }
    }

    @Override
    public void get(long start, long end, int[] buffer) {
        for (int i = 0; i < end - start; i++) {
            buffer[i] = segment.getAtIndex(JAVA_INT, start + i);
        }
    }

    @Override
    public void set(long at, int val) {
        segment.setAtIndex(JAVA_INT, at, val);
    }

    @Override
    public void set(long start, long end, IntBuffer buffer, int bufferStart) {
        for (int i = 0; i < end - start; i++) {
            set(start + i, buffer.get(bufferStart + i));
        }
    }

    @Override
    public synchronized void close() {
        if (arena != null && !closed) {
            arena.close();
        }
        closed = true;
    }

    @Override
    public long size() {
        return segment.byteSize() / JAVA_INT.byteSize();
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return segment.asByteBuffer();
    }

    @Override
    public void write(Path filename) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var destSegment = SegmentIntArray.fromMmapReadWrite(arena, filename, 0, segment.byteSize());

            destSegment.segment.copyFrom(segment);
            destSegment.force();
        }
    }

    @Override
    public void force() {
        if (segment.isMapped()) {
            segment.force();
        }
    }

    public ArrayRangeReference<IntArray> directRangeIfPossible(long start, long end) {
        return new ArrayRangeReference<>(this, start, end);
    }

    @Override
    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException {

        final int stride = 1024*1204*128; // Copy 1 GB at a time 'cause byte buffers are 'a byte buffering

        long ss = sourceStart;
        for (long as = arrayStart; as < arrayEnd; as += stride, ss += stride) {
            long ae = Math.min(as + stride, arrayEnd);

            long index = as * JAVA_INT.byteSize();
            long length = (ae - as) * JAVA_INT.byteSize();

            var bufferSlice = segment.asSlice(index, length).asByteBuffer();

            long startPos = ss * JAVA_INT.byteSize();
            while (bufferSlice.position() < bufferSlice.capacity()) {
                source.read(bufferSlice, startPos + bufferSlice.position());
            }
        }

    }

}
