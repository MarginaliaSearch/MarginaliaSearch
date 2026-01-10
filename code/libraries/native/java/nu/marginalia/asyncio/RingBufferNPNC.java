package nu.marginalia.asyncio;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RingBufferNPNC<T> {
    final Object[] items;

    final AtomicInteger writePos = new AtomicInteger(0);
    final AtomicInteger readPos = new AtomicInteger(0);
    final AtomicLong writerCtr = new AtomicLong(0);
    final AtomicLong writeMutex = new AtomicLong(0);
    volatile boolean closed = false;

    public RingBufferNPNC(int len) {
        items = new Object[len];
    }

    public boolean isClosed() {
        return closed;
    }
    public void close() {
        closed = true;
    }

    @SuppressWarnings("unchecked")
    public T take() {
        int rp;
        for (int iter = 0;;iter++) {
            while ((rp = readPos.get()) != writePos.get()) {
                Object ret = items[rp];
                if (readPos.compareAndSet(rp, (rp + 1) % items.length)) {
                    return (T) ret;
                }
            }
            if (iter > 1000)
                Thread.yield();
        }
    }

    @SuppressWarnings("unchecked")
    public T tryTake() {
        int rp;

        if ((rp = readPos.get()) != writePos.get()) {
            Object ret = items[rp];
            if (readPos.compareAndSet(rp, (rp + 1) % items.length)) {
                return (T) ret;
            }
        }

        return null;
    }

    public long lockWrite() {
        long selfId = writerCtr.incrementAndGet();
        // spin on write mutex condition
        while (!writeMutex.compareAndSet(0, selfId))
            Thread.yield();
        return selfId;
    }

    public void unlockWrite(long lockId) {
        if (!writeMutex.compareAndSet(lockId, 0))
            throw new IllegalStateException("Lock not held");
    }


    public boolean put(T item, long lockId) {

        int nextPos = (writePos.get() + 1) % items.length;
        if (nextPos == readPos.get()) {
            return false;
        }

        items[writePos.get()] = item;
        writePos.lazySet(nextPos);
        return true;
    }
}
