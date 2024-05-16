package nu.marginalia.array.page;

import nu.marginalia.NativeAlgos;
import nu.marginalia.array.ArrayRangeReference;
import nu.marginalia.array.LongArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** Variant of SegmentLongArray that uses Unsafe to access the memory.
 * */
public class UnsafeLongArray implements PartitionPage, LongArray {

    private static final Unsafe unsafe = UnsafeProvider.getUnsafe();
    private static final Logger logger = LoggerFactory.getLogger(UnsafeLongArray.class);

    @Nullable
    private final Arena arena;
    @Nullable
    private final FileChannel channel;

    private final MemorySegment segment;
    private boolean closed;

    UnsafeLongArray(MemorySegment segment,
                    @Nullable Arena arena) {
        this.segment = segment;
        this.arena = arena;
        this.channel = null;
    }

    UnsafeLongArray(MemorySegment segment,
                    @Nonnull FileChannel channel,
                    @Nullable Arena arena) {
        this.segment = segment;
        this.arena = arena;
        this.channel = channel;
    }

    public static UnsafeLongArray onHeap(Arena arena, long size) {
        return new UnsafeLongArray(arena.allocate(WORD_SIZE*size, 16), arena);
    }

    public static UnsafeLongArray fromMmapReadOnly(Arena arena, Path file, long offset, long size) throws IOException {
        try (var channel = (FileChannel) Files.newByteChannel(file, StandardOpenOption.READ)) {
            return new UnsafeLongArray(channel.map(FileChannel.MapMode.READ_ONLY,
                    JAVA_LONG.byteSize() * offset, JAVA_LONG.byteSize() * size,
                    arena), arena);
        }
        catch (IOException ex) {
            throw new IOException("Failed to map file " + file + " (" + offset + ":" + size + ")", ex);
        }
    }

    public static UnsafeLongArray fromMmapReadWrite(Arena arena, Path file, long offset, long size) throws IOException {
        var channel = (FileChannel) Files.newByteChannel(file,
                StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        var segment = channel.map(FileChannel.MapMode.READ_WRITE,
                JAVA_LONG.byteSize() * offset, JAVA_LONG.byteSize() * size,
                arena);

        return new UnsafeLongArray(segment, channel, arena);
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
        if (channel != null && !closed) {
            try {
                channel.close();
            }
            catch (IOException ex) {
                throw new RuntimeException("Failed to close channel", ex);
            }
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
            var destSegment = UnsafeLongArray.fromMmapReadWrite(arena, filename, 0, segment.byteSize() / JAVA_LONG.byteSize());

            destSegment.segment.copyFrom(segment);
            destSegment.force();
        }
    }

    @Override
    public void force() {
        if (segment.isMapped()) {
            segment.force();
            try {
                if (channel != null) {
                    channel.force(false);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to force channel", e);
            }
        }
    }

    public ArrayRangeReference<LongArray> directRangeIfPossible(long start, long end) {
        return new ArrayRangeReference<>(this, start, end);
    }

    public void chanelChannelTransfer(FileChannel source,
                                      long sourceStartL,
                                      long arrayStartL,
                                      long arrayEndL) throws IOException {

        assert channel != null;

        final int B_per_L = (int) JAVA_LONG.byteSize();

        final int strideB = 128*1024*1024; // Copy in 128 MB chunks

        final long destStartB = arrayStartL * B_per_L;
        final long destEndB = arrayEndL * B_per_L;
        final long lengthB = destEndB - destStartB;

        final long sourceStartB = sourceStartL * B_per_L;
        final long sourceEndB = sourceStartB + lengthB;


        if (sourceStartB > sourceEndB)
            throw new IndexOutOfBoundsException("Source start after end");
        if (sourceStartB > source.size())
            throw new IndexOutOfBoundsException("Source channel too small, start " + sourceStartB + " < input size " + source.size());
        if (sourceEndB > source.size())
            throw new IndexOutOfBoundsException("Source channel too small, end " + sourceEndB + " < input size " + source.size());

        long destIndexB = destStartB;

        source.position(sourceStartB);

        while (destIndexB < destEndB)
        {
            long stepSizeB = Math.min(destIndexB + strideB, destEndB);
            long copyLengthB = (stepSizeB - destIndexB);

            long transferred = channel.transferFrom(source, destIndexB, copyLengthB);
            if (transferred != copyLengthB) {
                logger.warn("Less than {} bytes were copied: {}", copyLengthB, transferred);
            }

            destIndexB += copyLengthB;
        }
    }

    @Override
    public void transferFrom(FileChannel source,
                             long sourceStartL,
                             long arrayStartL,
                             long arrayEndL) throws IOException {


        if (channel != null) {
            chanelChannelTransfer(source, sourceStartL, arrayStartL, arrayEndL);
            return;
        }

        final int B_per_L = (int) JAVA_LONG.byteSize();

        final int strideB = 1024*1024*1024; // Copy 1 GB at a time

        final long arrayStartB = arrayStartL * B_per_L;
        final long arrayEndB = arrayEndL * B_per_L;
        final long arrayLengthB = arrayEndB - arrayStartB;

        final long sourceStartB = sourceStartL * B_per_L;
        final long sourceEndB = sourceStartB + arrayLengthB;


        if (sourceStartB > sourceEndB)
            throw new IndexOutOfBoundsException("Source start after end");
        if (sourceStartB > source.size())
            throw new IndexOutOfBoundsException("Source channel too small, start " + sourceStartB + " < input size " + source.size());
        if (sourceEndB > source.size())
            throw new IndexOutOfBoundsException("Source channel too small, end " + sourceEndB + " < input size " + source.size());

        long channelIndexB = sourceStartB;
        long segmentIndexB = arrayStartB;

        while (segmentIndexB < arrayEndB)
        {
            long segmentEndB = Math.min(segmentIndexB + strideB, arrayEndB);
            long lengthB = (segmentEndB - segmentIndexB);

            var bufferSlice = segment.asSlice(segmentIndexB, lengthB).asByteBuffer();

            while (bufferSlice.position() < bufferSlice.capacity()) {
                if (source.position() + bufferSlice.capacity() > sourceEndB)
                    throw new IndexOutOfBoundsException("Source channel too small");

                if (source.read(bufferSlice, channelIndexB + bufferSlice.position()) < 0)
                    throw new IOException("Failed to read from source");
            }

            channelIndexB += lengthB;
            segmentIndexB += lengthB;
        }
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

}
