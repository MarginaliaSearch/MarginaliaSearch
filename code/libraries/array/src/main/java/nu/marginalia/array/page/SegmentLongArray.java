package nu.marginalia.array.page;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.array.ArrayRangeReference;
import nu.marginalia.array.LongArray;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SegmentLongArray implements PartitionPage, LongArray {

    @Nullable
    private final Arena arena;
    private final MemorySegment segment;
    private boolean closed;

    SegmentLongArray(MemorySegment segment,
                     @Nullable Arena arena) {
        this.segment = segment;
        this.arena = arena;
    }

    public static SegmentLongArray onHeap(Arena arena, long size) {
        return new SegmentLongArray(arena.allocate(WORD_SIZE*size, 8), arena);
    }

    public static SegmentLongArray fromMmapReadOnly(Arena arena, Path file, long offset, long size) throws IOException {
        return new SegmentLongArray(
                mmapFile(arena, file, offset, size, FileChannel.MapMode.READ_ONLY, StandardOpenOption.READ),
                arena);
    }

    public static SegmentLongArray fromMmapReadWrite(Arena arena, Path file, long offset, long size) throws IOException {

        return new SegmentLongArray(
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
                            WORD_SIZE * offset,
                            WORD_SIZE * size,
                            arena);
        }
        catch (IOException ex) {
            throw new IOException("Failed to map file " + file + " (" + offset + ":" + size + ")", ex);
        }
    }

    @Override
    public long get(long at) {
        try {
            return segment.getAtIndex(ValueLayout.JAVA_LONG, at);
        }
        catch (IndexOutOfBoundsException ex) {
            throw new IndexOutOfBoundsException("@" + at + "(" + 0 + ":" + segment.byteSize()/8 + ")");
        }
    }

    @Override
    public void get(long start, long end, long[] buffer) {
        for (int i = 0; i < end - start; i++) {
            buffer[i] = segment.getAtIndex(ValueLayout.JAVA_LONG, start + i);
        }
    }

    @Override
    public void set(long at, long val) {
        segment.setAtIndex(ValueLayout.JAVA_LONG, at, val);
    }

    @Override
    public void set(long start, long end, LongBuffer buffer, int bufferStart) {
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
        return segment.byteSize() / 8;
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return segment.asByteBuffer();
    }

    @Override
    public void write(Path filename) throws IOException {
        try (var channel = (FileChannel) Files.newByteChannel(filename, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            write(channel);
        }
    }

    @Override
    public void force() {
        if (segment.isMapped()) {
            segment.force();
        }
    }

    public ArrayRangeReference<LongArray> directRangeIfPossible(long start, long end) {
        return new ArrayRangeReference<>(this, start, end);
    }

    @Override
    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException {

        long index = arrayStart * WORD_SIZE;
        long length = (arrayEnd - arrayStart) * WORD_SIZE;

        var bufferSlice = segment.asSlice(index, length).asByteBuffer();

        long startPos = sourceStart * WORD_SIZE;
        while (bufferSlice.position() < bufferSlice.capacity()) {
            source.read(bufferSlice, startPos + bufferSlice.position());
        }
    }

    @Override
    public void advice(NativeIO.Advice advice) throws IOException {
//        NativeIO.madvise((MappedByteBuffer) byteBuffer, advice);
    }

    @Override
    public void advice(NativeIO.Advice advice, long start, long end) throws IOException {
//        NativeIO.madviseRange((MappedByteBuffer) byteBuffer, advice, (int) start, (int) (end-start));
    }

}
