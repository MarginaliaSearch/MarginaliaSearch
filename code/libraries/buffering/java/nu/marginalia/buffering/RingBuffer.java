package nu.marginalia.buffering;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RingBuffer<T> {
    final Object[] items;

    final AtomicInteger writePos = new AtomicInteger(0);
    final AtomicInteger readPos = new AtomicInteger(0);
    final AtomicReference<T> toWrite = new AtomicReference<T>();

    volatile boolean closed = false;

    public RingBuffer(int len) {
        items = new Object[len];
    }

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    @SuppressWarnings("unchecked")
    public T takeNC() {
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

    public T take1C() {
        int rp;
        for (int iter = 0;;iter++) {
            if ((rp = readPos.getOpaque()) != writePos.get()) {
                Object ret = items[rp];
                readPos.setRelease((rp + 1) % items.length);
                return (T) ret;
            }
            if (iter > 1000)
                Thread.yield();
        }
    }

    public T tryTakeNC() {
        int rp;
        if ((rp = readPos.get()) != writePos.get()) {
            Object ret = items[rp];
            if (readPos.compareAndSet(rp, (rp + 1) % items.length)) {
                return (T) ret;
            }
        }
        return null;
    }

    public T tryTake1C() {
        int rp;

        if ((rp = readPos.getOpaque()) != writePos.get()) {
            Object ret = items[rp];
            readPos.setRelease((rp + 1) % items.length);
            return (T) ret;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public int tryTake1C(T[] ret) {

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
    public T peek() {
        int rp;
        if ((rp = readPos.get()) != writePos.get()) {
            return (T) items[rp];
        }
        return null;
    }

    public boolean put(T item) {
        int wp = writePos.getOpaque();
        int nextPos = (wp + 1) % items.length;
        if (nextPos == readPos.getAcquire()) {
            return false;
        }
        items[wp] = item;

        writePos.set(nextPos);

        return true;
    }

    public boolean putNP(T item) {
        if (!toWrite.weakCompareAndSetAcquire(null, item)) {
            return false;
        }

        int wp = writePos.getAcquire();
        int nextPos = (wp + 1) % items.length;
        if (nextPos == readPos.getAcquire()) {
            toWrite.setRelease(null);
            return false;
        }

        items[wp] = item;

        writePos.setRelease(nextPos);
        toWrite.setRelease(null);

        return true;
    }

    public double capacityHint() {
        int rp = readPos.get();
        int wp = writePos.get();

        return ((rp - wp - 1 + items.length) % items.length) / (double) items.length;
    }

    public void reset() {
        writePos.set(0);
        readPos.set(0);
        toWrite.set(null);
        Arrays.fill(items, null);
        closed = false;
    }
}
