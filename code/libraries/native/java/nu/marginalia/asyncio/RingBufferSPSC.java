package nu.marginalia.asyncio;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RingBufferSPSC<T> {
    final Object[] items;

    final AtomicInteger writePos = new AtomicInteger(0);
    final AtomicInteger readPos = new AtomicInteger(0);
    final AtomicReference<T> toWrite = new AtomicReference<T>();

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

        if (!readPos.compareAndSet(rp, (rp+i) % items.length))
            throw new IllegalStateException("read pos changed, multiple readers?");

        return i;
    }

    public boolean put(T item) {
        int wp = writePos.get();
        int nextPos = (wp + 1) % items.length;
        if (nextPos == readPos.get()) {
            return false;
        }
        items[wp] = item;
        if (!writePos.compareAndSet(wp, nextPos))
            throw new IllegalStateException("write pos changed, multiple readers?");

        return true;
    }

    public boolean putNP(T item) {
        if (!toWrite.weakCompareAndSetRelease(null, item)) {
            return false;
        }

        int wp = writePos.get();
        int nextPos = (wp + 1) % items.length;
        if (nextPos == readPos.get()) {
            toWrite.set(null);
            return false;
        }

        items[wp] = item;

        writePos.lazySet(nextPos);
        toWrite.setRelease(null);

        return true;
    }
}
