package nu.marginalia.array.pool;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicInteger;

public interface MemoryPage extends AutoCloseable {
    boolean isHeld();

    MemorySegment getMemorySegment();

    byte getByte(int offset);
    int getInt(int offset);
    long getLong(int offset);

    int binarySearchLong(long key, int baseOffset, int fromIndex, int toIndex);
    boolean acquireForWriting(long intendedAddress);
    boolean acquireAsReader(long expectedAddress);

    AtomicInteger pinCount();

    void increaseClock(int val);
    void touchClock(int val);
    boolean decreaseClock();

    long pageAddress();
    void pageAddress(long address);

    boolean dirty();
    void dirty(boolean val);

    void close();
}
