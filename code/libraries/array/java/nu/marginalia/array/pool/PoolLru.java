package nu.marginalia.array.pool;

import nu.marginalia.array.page.UnsafeLongArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.SequencedCollection;
import java.util.concurrent.locks.StampedLock;

/** LRU for pool buffers
 * */
public class PoolLru {
    private static final Logger logger = LoggerFactory.getLogger(PoolLru.class);

    private final int maxSize;
    private final LinkedHashMap<Long, UnsafeLongArrayBuffer> backingMap;
    private final UnsafeLongArrayBuffer[] pages;
    private final SequencedCollection<UnsafeLongArrayBuffer> values;
    private final ArrayDeque<UnsafeLongArrayBuffer> freeQueue;
    private final int freeQueueSize;

    private final StampedLock mapLock = new StampedLock();
    private final StampedLock freeQueueLock = new StampedLock();

    public PoolLru(UnsafeLongArrayBuffer[] pages) {
        backingMap = new LinkedHashMap<>(pages.length, 0.75f, true);
        this.pages = pages;
        // Pre-assign all entries with nonsense memory locations
        for (int i = 0; i < pages.length; i++) {
            backingMap.put(-i-1L, pages[i]);
        }
        values = backingMap.sequencedValues();
        maxSize = backingMap.size();

        freeQueueSize = Math.clamp(maxSize / 4, 4, 128);
        freeQueue = new ArrayDeque<>(freeQueueSize);
    }

    /** Attempt to get a buffer alread yassociated with the address */
    public UnsafeLongArrayBuffer get(long address) {
        long stamp = mapLock.readLock();
        try {
            return backingMap.get(address);
        }
        finally {
            mapLock.unlockRead(stamp);
        }
    }

    /** Associate the buffer with an address */
    public void register(UnsafeLongArrayBuffer buffer) {
        long stamp = mapLock.writeLock();
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
            mapLock.unlockWrite(stamp);
        }
    }

    /** Attempt to get a free buffer from the pool
     *
     * @return An unheld buffer, or null if the attempt failed
     * */
    public UnsafeLongArrayBuffer getFree() {
        long queueLock = freeQueueLock.writeLock();
        try {
            UnsafeLongArrayBuffer buffer = freeQueue.pollFirst();
            if (buffer != null && !buffer.isHeld()) {
                return buffer;
            }

            long mapStamp = mapLock.writeLock();
            try {
                var iter = values.iterator();
                while (iter.hasNext() && freeQueue.size() < freeQueueSize) {
                    buffer = iter.next();
                    if (!buffer.isHeld()) {
                        iter.remove();
                        freeQueue.addLast(buffer);
                    }
                }

                logger.warn("Running expensive reclamation");
                if (freeQueue.isEmpty()) {
                    for (var page : pages) {
                        if (!page.isHeld()) {
                            freeQueue.addLast(page);
                        }
                        if (freeQueue.size() >= freeQueueSize) {
                            break;
                        }
                    }
                }
                return freeQueue.pollFirst();
            }
            finally {
                mapLock.unlockWrite(mapStamp);
            }
        }
        finally {
            freeQueueLock.unlockWrite(queueLock);
        }
    }
}
