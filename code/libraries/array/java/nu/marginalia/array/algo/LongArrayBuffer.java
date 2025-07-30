package nu.marginalia.array.algo;

import nu.marginalia.array.pool.BufferEvictionPolicy;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicInteger;

public interface LongArrayBuffer {
    boolean isHeld();

    boolean acquireForWriting(long intendedAddress);
    boolean acquireAsReader(long expectedAddress);
    long accessOrder();
    MemorySegment getMemorySegment();

    BufferEvictionPolicy evictionPolicy();
    void evictionPolicy(BufferEvictionPolicy evictionPolicy);

    long pageAddress();
    void pageAddress(long address);

    AtomicInteger pinCount();

    boolean dirty();
    void dirty(boolean val);

    void close();
}
