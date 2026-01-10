package nu.marginalia.asyncio;

import java.util.concurrent.atomic.AtomicInteger;

public class RingBufferSPSC<T> {
    final Object[] items;

    final AtomicInteger writePos = new AtomicInteger(0);
    final AtomicInteger readPos = new AtomicInteger(0);

    volatile boolean closed = false;

    public RingBufferSPSC(int len) {
        items = new Object[len];
    }

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    @SuppressWarnings("unchecked")
    public T take() {
        int rp;
        for (int iter = 0;;iter++) {
            if ((rp = readPos.get()) != writePos.get()) {
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
    public int tryTake(T[] ret) {

        int rp = readPos.get();
        int wp = writePos.get();
        if (rp == wp)
            return 0;

        int i = 0;
        int nrp;
        while (i < ret.length && (nrp = (rp+i) % items.length) != wp) {
            ret[i++] = (T) items[nrp];
        }

        readPos.lazySet((rp+i) % items.length);

        return i;
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

    @SuppressWarnings("unchecked")
    public T peek() {
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
