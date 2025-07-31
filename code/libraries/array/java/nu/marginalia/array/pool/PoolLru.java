package nu.marginalia.array.pool;

import nu.marginalia.array.page.UnsafeLongArrayBuffer;

import java.util.LinkedHashMap;
import java.util.SequencedCollection;
import java.util.concurrent.locks.StampedLock;

/** LRU for pool buffers
 * */
public class PoolLru {
    private final int maxSize;
    private final LinkedHashMap<Long, UnsafeLongArrayBuffer> backingMap;
    private final SequencedCollection<UnsafeLongArrayBuffer> values;

    private final StampedLock lock = new StampedLock();

    public PoolLru(UnsafeLongArrayBuffer[] entries) {
        backingMap = new LinkedHashMap<>(entries.length, 0.75f, true);
        // Pre-assign all entries with nonsense memory locations
        for (int i = 0; i < entries.length; i++) {
            backingMap.put(-i-1L, entries[i]);
        }
        values = backingMap.sequencedValues().reversed();
        maxSize = backingMap.size();
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
    public void put(long address, UnsafeLongArrayBuffer buffer) {
        long stamp = lock.writeLock();
        try {
            backingMap.put(address, buffer);
            // Evict the last entry if we've exceeded the
            while (backingMap.size() >= maxSize) {
                backingMap.pollLastEntry();
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
            var iter = values.iterator();
            UnsafeLongArrayBuffer buffer = null;
            int attempts = 0;
            while (iter.hasNext() && attempts++ < 5) {
                buffer = iter.next();
                if (!buffer.isHeld()) {
                    iter.remove();
                    break;
                }
            }

            return buffer;
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }
}
