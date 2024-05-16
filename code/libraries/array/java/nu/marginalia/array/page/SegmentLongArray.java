package nu.marginalia.array.page;

import nu.marginalia.NativeAlgos;
import nu.marginalia.array.ArrayRangeReference;
import nu.marginalia.array.LongArray;

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
        return new SegmentLongArray(arena.allocate(WORD_SIZE*size, 16), arena);
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
        return new SegmentLongArray(
            segment.asSlice(
                start * JAVA_LONG.byteSize(),
                (end-start) * JAVA_LONG.byteSize()),
            null);
    }

    @Override
    public LongArray shifted(long start) {
        return new SegmentLongArray(
                segment.asSlice(start * JAVA_LONG.byteSize()),
            null);
    }

    @Override
    public long get(long at) {
        try {
            return segment.getAtIndex(JAVA_LONG, at);
        }
        catch (IndexOutOfBoundsException ex) {
            throw new IndexOutOfBoundsException("@" + at + "(" + 0 + ":" + segment.byteSize()/8 + ")");
        }
    }

    @Override
    public void get(long start, long end, long[] buffer) {
        for (int i = 0; i < end - start; i++) {
            buffer[i] = segment.getAtIndex(JAVA_LONG, start + i);
        }
    }

    @Override
    public void set(long at, long val) {
        segment.setAtIndex(JAVA_LONG, at, val);
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
        return segment.byteSize() / JAVA_LONG.byteSize();
    }

    @Override
    public void quickSortNative(long start, long end) {
        NativeAlgos.sort(segment, start, end);
    }
    @Override
    public void quickSortNative128(long start, long end) {
        NativeAlgos.sort128(segment, start, end);
    }

    @Override
    public long linearSearchNative(long key, long start, long end) {
        return NativeAlgos.linearSearch64(key, segment, start, end);
    }

    @Override
    public long linearSearchNative128(long key, long start, long end) {
        return NativeAlgos.linearSearch128(key, segment, start, end);
    }

    @Override
    public long binarySearchNativeUB(long key, long start, long end) {
        return NativeAlgos.binarySearch64Upper(key, segment, start, end);
    }

    @Override
    public long binarySearchNative128(long key, long start, long end) {
        return NativeAlgos.binarySearch128(key, segment, start, end);
    }


    @Override
    public ByteBuffer getByteBuffer() {
        return segment.asByteBuffer();
    }

    @Override
    public void write(Path filename) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var destSegment = SegmentLongArray.fromMmapReadWrite(arena, filename, 0, segment.byteSize() / JAVA_LONG.byteSize());

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

        final int stride = 1024*1024*128; // Copy 1 GB at a time 'cause byte buffers are 'a byte buffering

        if (source.size() / 8 < sourceStart + (arrayEnd - arrayStart)) {
            throw new IndexOutOfBoundsException(STR."Source channel too small: \{source.size()} < \{sourceStart + (arrayEnd - arrayStart)}");
        }

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
