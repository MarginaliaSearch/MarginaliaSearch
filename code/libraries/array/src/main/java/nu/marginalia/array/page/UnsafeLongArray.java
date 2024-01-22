package nu.marginalia.array.page;

import nu.marginalia.array.ArrayRangeReference;
import nu.marginalia.array.LongArray;
import sun.misc.Unsafe;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** Variant of SegmentLongArray that uses Unsafe to access the memory.
 * */
public class UnsafeLongArray implements PartitionPage, LongArray {

    private static final Unsafe unsafe = UnsafeProvider.getUnsafe();

    @Nullable
    private final Arena arena;
    private final MemorySegment segment;
    private boolean closed;

    UnsafeLongArray(MemorySegment segment,
                    @Nullable Arena arena) {
        this.segment = segment;
        this.arena = arena;
    }

    public static UnsafeLongArray onHeap(Arena arena, long size) {
        return new UnsafeLongArray(arena.allocate(WORD_SIZE*size, 8), arena);
    }

    public static UnsafeLongArray fromMmapReadOnly(Arena arena, Path file, long offset, long size) throws IOException {
        return new UnsafeLongArray(
                mmapFile(arena, file, offset, size, FileChannel.MapMode.READ_ONLY, StandardOpenOption.READ),
                arena);
    }

    public static UnsafeLongArray fromMmapReadWrite(Arena arena, Path file, long offset, long size) throws IOException {

        return new UnsafeLongArray(
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
                            JAVA_LONG.byteSize() * offset,
                            JAVA_LONG.byteSize() * size,
                            arena);
        }
        catch (IOException ex) {
            throw new IOException("Failed to map file " + file + " (" + offset + ":" + size + ")", ex);
        }
    }

    @Override
    public LongArray range(long start, long end) {
        return new UnsafeLongArray(
            segment.asSlice(
                start * JAVA_LONG.byteSize(),
                (end-start) * JAVA_LONG.byteSize()),
            null);
    }

    @Override
    public LongArray shifted(long start) {
        return new UnsafeLongArray(
                segment.asSlice(start * JAVA_LONG.byteSize()),
            null);
    }

    @Override
    public long get(long at) {
        try {
            return unsafe.getLong(segment.address() + at * JAVA_LONG.byteSize());
        }
        catch (IndexOutOfBoundsException ex) {
            throw new IndexOutOfBoundsException("@" + at + "(" + 0 + ":" + segment.byteSize()/8 + ")");
        }
    }

    @Override
    public void get(long start, long end, long[] buffer) {
        for (int i = 0; i < end - start; i++) {
            buffer[i] = unsafe.getLong(segment.address() + (start + i) * JAVA_LONG.byteSize());
        }
    }

    @Override
    public void set(long at, long val) {
        unsafe.putLong(segment.address() + at * JAVA_LONG.byteSize(), val);
    }

    @Override
    public void set(long start, long end, LongBuffer buffer, int bufferStart) {
        for (int i = 0; i < end - start; i++) {
            unsafe.putLong(segment.address() + (start + i) * JAVA_LONG.byteSize(), buffer.get(bufferStart + i));
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
        return segment.byteSize() / JAVA_LONG.byteSize();
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return segment.asByteBuffer();
    }

    @Override
    public void write(Path filename) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var destSegment = UnsafeLongArray.fromMmapReadWrite(arena, filename, 0, segment.byteSize());

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

    public ArrayRangeReference<LongArray> directRangeIfPossible(long start, long end) {
        return new ArrayRangeReference<>(this, start, end);
    }

    @Override
    public void transferFrom(FileChannel source, long sourceStart, long arrayStart, long arrayEnd) throws IOException {

        final int stride = 1024*1204*128; // Copy 1 GB at a time 'cause byte buffers are 'a byte buffering

        long ss = sourceStart;
        for (long as = arrayStart; as < arrayEnd; as += stride, ss += stride) {
            long ae = Math.min(as + stride, arrayEnd);

            long index = as * JAVA_LONG.byteSize();
            long length = (ae - as) * JAVA_LONG.byteSize();

            var bufferSlice = segment.asSlice(index, length).asByteBuffer();

            long startPos = ss * JAVA_LONG.byteSize();
            while (bufferSlice.position() < bufferSlice.capacity()) {
                source.read(bufferSlice, startPos + bufferSlice.position());
            }
        }

    }

}
