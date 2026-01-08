package nu.marginalia.asyncio;

import java.util.concurrent.atomic.AtomicInteger;

public class LongRingBufferSPSC {
    final long[] items;

    final AtomicInteger writePos = new AtomicInteger(0);
    final AtomicInteger readPos = new AtomicInteger(0);

    public LongRingBufferSPSC(int len) {
        items = new long[len];
    }

    @SuppressWarnings("unchecked")
    long take() {
        int rp;
        for (;;) {
            while ((rp = readPos.get()) != writePos.get()) {
                var ret = items[rp];
                readPos.lazySet((rp + 1) % items.length);
                return ret;
            }
            Thread.yield();
        }
    }

    int takeNonBlock(long[] buffer) {
        final int rp = readPos.get();
        final int wp = writePos.get();

        final int available = (wp - rp + items.length) % items.length;
        final int batchSize = Math.min(buffer.length, available);

        for (int i = 0; i < batchSize; i++) {
            buffer[i] = items[(rp + i) % items.length];
        }

        if (batchSize > 0) {
            readPos.lazySet((rp + batchSize) % items.length);
        }

        return batchSize;
    }

    void put(long value) {
        int nextPos = (writePos.get() + 1) % items.length;
        while (nextPos == readPos.get()) {
            Thread.yield();
        }
        items[writePos.get()] = value;
        writePos.lazySet(nextPos);
    }

    void put(long[] values, int n) {
        int i;
        final int wp = writePos.get();
        final int rp = readPos.get();

        final int available = (rp - wp - 1 + items.length) % items.length;
        final int batchSize = Math.min(n, available);

        for (i = 0; i < batchSize; i++) {
            items[(wp+i)%items.length] = values[i];
        }
        writePos.lazySet((wp + i) % items.length);

        for (; i < n; i++)
            put(values[i]);
    }
}
