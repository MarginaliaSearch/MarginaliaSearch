package nu.marginalia.asyncio;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LongRingBufferNPSC {
    final long[] items;
    final int mask;

    final AtomicInteger writePos = new AtomicInteger(0);
    final AtomicInteger readPos = new AtomicInteger(0);
    final AtomicLong writerCtr = new AtomicLong(0);
    final AtomicLong writeMutex = new AtomicLong(0);
    final AtomicBoolean closed = new AtomicBoolean(false);

    public LongRingBufferNPSC(int len) {
        items = new long[len];
        mask = items.length - 1;
        assert (len & mask) == 0;
    }

    public void close() {
        closed.lazySet(true);
    }

    @SuppressWarnings("unchecked")
    public long take() {
        int rp;
        for (int iter = 0;;iter++) {
            while ((rp = readPos.get()) != writePos.get()) {
                var ret = items[rp];
                readPos.lazySet((rp + 1) & mask);
                return ret;
            }
            if (iter > 1000)
                Thread.yield();
        }
    }

    public int takeNonBlock(long[] buffer) {
        final int rp = readPos.get();
        final int wp = writePos.get();

        final int available = (wp - rp + items.length) & mask;
        final int batchSize = Math.min(buffer.length, available);

        for (int i = 0; i < batchSize; i++) {
            buffer[i] = items[(rp + i) & mask];
        }

        if (batchSize > 0) {
            readPos.lazySet((rp + batchSize) & mask);
        }

        return batchSize;
    }

    public void put(long value, long lock) {
        if (closed.get())
            return;

        int nextPos = (writePos.get() + 1) & mask;
        for (int iter = 0; nextPos == readPos.get(); iter++) {
            if (iter > 1000) {
                if (closed.get())
                    return;
                Thread.yield();
            }
        }
        items[writePos.get()] = value;
        writePos.lazySet(nextPos);
    }

    public void put(long[] values, long lock, int n) {
        int i;
        final int wp = writePos.get();
        final int rp = readPos.get();

        final int available = (rp - wp - 1 + items.length) & mask;
        final int batchSize = Math.min(n, available);

        for (i = 0; i < batchSize; i++) {
            items[(wp+i)%items.length] = values[i];
        }
        writePos.lazySet((wp + i) & mask);

        for (; i < n; i++)
            put(values[i], lock);
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

}
