package nu.marginalia.buffering;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LongRingBuffer {
    final long[] items;
    final int mask;

    final AtomicInteger writePos = new AtomicInteger(0);
    final AtomicInteger readPos = new AtomicInteger(0);
    final AtomicBoolean writeStage = new AtomicBoolean();

    public LongRingBuffer(int len) {
        items = new long[len];
        mask = items.length - 1;
        assert (len & mask) == 0;
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

    public void put(long value) {
        int nextPos = (writePos.get() + 1) & mask;
        for (int iter = 0; nextPos == readPos.get(); iter++) {
            if (iter > 1000) Thread.yield();
        }
        items[writePos.get()] = value;
        writePos.set(nextPos);
    }

    public void put(long[] values, int n) {
        int i;
        final int wp = writePos.get();
        final int rp = readPos.get();

        final int available = (rp - wp - 1 + items.length) & mask;
        final int batchSize = Math.min(n, available);

        for (i = 0; i < batchSize; i++) {
            items[(wp+i)%items.length] = values[i];
        }
        writePos.set((wp + i) & mask);

        for (; i < n; i++)
            put(values[i]);
    }


    public boolean putNP(long value) {
        if (!writeStage.weakCompareAndSetRelease(false, true)) {
            return false;
        }

        int wp = writePos.get();
        int nextPos = (wp + 1) % items.length;
        if (nextPos == readPos.get()) {
            writeStage.setRelease(false);
            return false;
        }

        items[wp] = value;

        writePos.set(nextPos);
        writeStage.setRelease(false);

        return true;
    }
}
