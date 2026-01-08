package nu.marginalia.asyncio;

import java.util.concurrent.atomic.AtomicInteger;

public class RingBufferSPNC<T> {
    final Object[] items;

    final AtomicInteger writePos = new AtomicInteger(0);
    final AtomicInteger readPos = new AtomicInteger(0);

    public RingBufferSPNC(int len) {
        items = new Object[len];
    }

    @SuppressWarnings("unchecked")
    T take() {
        int rp;
        for (;;) {
            while ((rp = readPos.get()) != writePos.get()) {
                Object ret = items[rp];
                if (readPos.compareAndSet(rp, (rp + 1) % items.length)) {
                    return (T) ret;
                }
            }
            Thread.yield();
        }
    }

    @SuppressWarnings("unchecked")
    T peek() {
        int rp;
        if ((rp = readPos.get()) != writePos.get()) {
            return (T) items[rp];
        }
        return null;
    }

    public boolean put(T item) {
        int nextPos = (writePos.get() + 1) % items.length;
        if (nextPos == readPos.get()) {
            return false;
        }
        items[writePos.get()] = item;
        // single producer, safe
        writePos.lazySet(nextPos);
        return true;
    }
}
