package nu.marginalia.array.page;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.algo.LongArrayBuffer;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** Variant of SegmentLongArray that uses Unsafe to access the memory.
 * */

@SuppressWarnings("preview")
public class UnsafeLongArrayBuffer implements LongArray, LongArrayBuffer {

    private static final Unsafe unsafe = UnsafeProvider.getUnsafe();

    private final MemorySegment segment;
    public final int ord;

    private volatile long pageAddress = -1;
    private volatile boolean dirty = false;

    /** Pin count is used as a read-write condition.
     * <p></p>
     * When the pin count is 0, the page is free.
     * When it is -1, it is held for writing.
     * When it is greater than 0, it is held for reading.
     */
    private final AtomicInteger pinCount = new AtomicInteger(0);
    private final AtomicInteger clock = new AtomicInteger();

    public UnsafeLongArrayBuffer(MemorySegment segment, int ord) {
        this.segment = segment;
        this.ord = ord;
    }

    public int hashCode() {
        return (int) segment.address();
    }
    public boolean equals(Object obj) {
        return obj == this;
    }

    public void increaseClock(int val) {
        clock.addAndGet(val);
    }
    public void touchClock(int val) {
        clock.set(val);
    }
    public boolean decreaseClock() {
        for (;;) {
            int cv = clock.get();
            if (cv == 0)
                return true;
            if (clock.compareAndSet(cv, cv-1)) {
                return cv == 1;
            }
        }
    }

    @Override
    public long pageAddress() {
        return pageAddress;
    }

    @Override
    public void pageAddress(long address) {
        this.pageAddress = address;
    }

    @Override
    public AtomicInteger pinCount() {
        return pinCount;
    }

    @Override
    public boolean dirty() {
        return dirty;
    }

    @Override
    public void dirty(boolean val) {
        this.dirty = val;
    }

    @Override
    public boolean isHeld() {
        return 0 != this.pinCount.get();
    }

    public byte getByte(int offset) {
        return unsafe.getByte(segment.address() + offset);
    }
    public int getInt(int offset) {
        return unsafe.getInt(segment.address() + offset);
    }
    public long getLong(int offset) {
        return unsafe.getLong(segment.address() + offset);
    }

    public int binarySearchLong(long key, int baseOffset, int fromIndex, int toIndex) {
        int low = 0;
        int high = (toIndex - fromIndex) - 1;
        int len = high - low;

        while (len > 0) {
            var half = len / 2;
            if (getLong(baseOffset + fromIndex + 8 * (low + half)) < key) {
                low += len - half;
            }
            len = half;
        }

        return fromIndex + low;
    }

    @Override
    public boolean acquireForWriting(long intendedAddress) {
        if (pinCount.compareAndSet(0, -1)) {
            pageAddress = intendedAddress;
            dirty = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean acquireAsReader(long expectedAddress) {
        int pinCountVal;

        while ((pinCountVal = pinCount.get()) >= 0) {
            if (pinCount.compareAndSet(pinCountVal, pinCountVal+1)) {
                if (pageAddress != expectedAddress) {
                    pinCount.decrementAndGet();
                    return false;
                }
                return true;
            }
        }

        return false;
    }


    /** Close yields the buffer back to the pool (unless held by multiple readers), but does not deallocate it */
    @Override
    public void close() {
        pinCount.decrementAndGet();
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
        assert at >= 0;
        assert at <= size() : at + " > " + size();
        if (at > size())
            throw new RuntimeException(at + ">" + size());
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
    public MemorySegment getMemorySegment() {
        return segment;
    }

    @Override
    public void set(long start, long end, LongBuffer buffer, int bufferStart) {
        for (int i = 0; i < end - start; i++) {
            unsafe.putLong(segment.address() + (start + i) * JAVA_LONG.byteSize(), buffer.get(bufferStart + i));
        }
    }

    @Override
    public long size() {
        return segment.byteSize() / JAVA_LONG.byteSize();
    }

    @Override
    public void write(Path filename) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void force() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void transferFrom(FileChannel source,
                             long sourceStartL,
                             long arrayStartL,
                             long arrayEndL) throws IOException {
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
    public void transferFrom(LongArray source,
                             long sourceStartL,
                             long destStartL,
                             long destEndL)
    {
        if (destStartL > destEndL)
            throw new IndexOutOfBoundsException("Source start after end");

        if (sourceStartL + (destEndL - destStartL) > source.size())
            throw new IndexOutOfBoundsException("Source array too small");
        if (destEndL > size())
            throw new IndexOutOfBoundsException("Destination array too small");

        MemorySegment.copy(
                source.getMemorySegment(), JAVA_LONG, sourceStartL * JAVA_LONG.byteSize(),
                segment, JAVA_LONG, destStartL * JAVA_LONG.byteSize(),
                destEndL - destStartL
        );

    }

}
