package nu.marginalia.array.algo;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicInteger;

public interface LongArrayBuffer {
    boolean isHeld();

    boolean acquireForWriting(long intendedAddress);
    boolean acquireAsReader(long expectedAddress);
    MemorySegment getMemorySegment();

    long pageAddress();
    void pageAddress(long address);

    AtomicInteger pinCount();

    boolean dirty();
    void dirty(boolean val);

    void close();
}
