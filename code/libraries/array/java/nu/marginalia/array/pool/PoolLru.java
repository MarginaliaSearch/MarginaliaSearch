package nu.marginalia.array.pool;

import nu.marginalia.array.page.UnsafeLongArrayBuffer;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.SequencedCollection;
import java.util.concurrent.locks.StampedLock;

/** LRU for pool buffers
 * */
public class PoolLru {
    private final int maxSize;
    private final LinkedHashMap<Long, UnsafeLongArrayBuffer> backingMap;
    private final SequencedCollection<UnsafeLongArrayBuffer> values;
    private final ArrayDeque<UnsafeLongArrayBuffer> freeQueue = new ArrayDeque<>();
    private final long freeQueueSize;

    private final StampedLock lock = new StampedLock();

    public PoolLru(UnsafeLongArrayBuffer[] entries) {
        backingMap = new LinkedHashMap<>(entries.length, 0.75f, true);
        // Pre-assign all entries with nonsense memory locations
        for (int i = 0; i < entries.length; i++) {
            backingMap.put(-i-1L, entries[i]);
        }
        values = backingMap.sequencedValues();
        maxSize = backingMap.size();

        freeQueueSize = Math.clamp(maxSize / 4, 4, 64);
    }

    /** Attempt to get a buffer alread yassociated with the address */
    public UnsafeLongArrayBuffer get(long address) {
        long stamp = lock.readLock();
        try {
            return backingMap.get(address);
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    /** Associate the buffer with an address */
    public void register(UnsafeLongArrayBuffer buffer) {
        long stamp = lock.writeLock();
        try {
            var old = backingMap.put(buffer.pageAddress(), buffer);
            if (old != null && !old.isHeld()) {
                freeQueue.add(old);
            }

            // Evict the last entry if we've exceeded the
            while (backingMap.size() >= maxSize) {
                var evicted = backingMap.pollLastEntry().getValue();
                if (!evicted.isHeld()) {
                    freeQueue.add(evicted);
                }
            }
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    /** Attempt to get a free buffer from the pool
     *
     * @return An unheld buffer, or null if the attempt failed
     * */
    public UnsafeLongArrayBuffer getFree() {
        long stamp = lock.writeLock();

        try {
            UnsafeLongArrayBuffer buffer = freeQueue.pollFirst();
            if (buffer != null && !buffer.isHeld()) {
                return buffer;
            }

            var iter = values.iterator();
            int attempts = 0;
            while (iter.hasNext() && attempts++ < freeQueueSize) {
                buffer = iter.next();
                if (!buffer.isHeld()) {
                    iter.remove();
                    freeQueue.addLast(buffer);
                }
            }
            return freeQueue.pollFirst();
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }
}
