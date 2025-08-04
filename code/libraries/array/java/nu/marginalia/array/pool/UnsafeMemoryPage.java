package nu.marginalia.array.pool;

import nu.marginalia.array.page.UnsafeProvider;
import sun.misc.Unsafe;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicInteger;

/** Variant of SegmentLongArray that uses Unsafe to access the memory.
 * */

@SuppressWarnings("preview")
public class UnsafeMemoryPage implements MemoryPage, AutoCloseable {

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

    public UnsafeMemoryPage(MemorySegment segment, int ord) {
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
        int len = toIndex - fromIndex;

        while (len > 0) {
            var half = len / 2;
            long val = getLong(baseOffset + 8 * (fromIndex + low + half));
            if (val < key) {
                low += len - half;
            }
            else if (val == key) {
                low += half;
                break;
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
    public MemorySegment getMemorySegment() {
        return segment;
    }

}
