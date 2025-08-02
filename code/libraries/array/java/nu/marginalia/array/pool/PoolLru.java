package nu.marginalia.array.pool;

import nu.marginalia.array.page.UnsafeLongArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashSet;
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
    private final HashSet<UnsafeLongArrayBuffer> freeSet = new HashSet<>();

    private final int freeQueueSize;

    private long junkAddress = Long.MIN_VALUE;
    private final StampedLock mapLock = new StampedLock();

    public PoolLru(UnsafeLongArrayBuffer[] pages) {
        backingMap = new LinkedHashMap<>(pages.length, 0.75f, true);
        this.pages = pages;
        // Pre-assign all entries with nonsense memory locations
        for (int i = 0; i < pages.length; i++) {
            backingMap.put(-i-1L, pages[i]);
        }
        values = backingMap.sequencedValues();
        maxSize = backingMap.size();

        freeQueueSize = maxSize / 4;
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
            UnsafeLongArrayBuffer old = backingMap.put(buffer.pageAddress(), buffer);
            if (old != null && !old.isHeld()) {
                if (freeSet.add(old)) {
                    freeQueue.add(old);
                }
            }


            // Evict the last entry if we've exceeded the
            while (backingMap.size() >= maxSize) {
                UnsafeLongArrayBuffer evicted = backingMap.pollFirstEntry().getValue();
                if (evicted != null && !evicted.isHeld()) {
                    if (freeSet.add(evicted)) {
                        freeQueue.add(evicted);
                    }
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
        long mapStamp = mapLock.writeLock();
        try {
            UnsafeLongArrayBuffer buffer;

            while (!freeQueue.isEmpty()) {
                buffer = freeQueue.pollFirst();
                if (buffer != null && !buffer.isHeld()) {
                    freeSet.remove(buffer);
                    return buffer;
                }
            }
            var iter = values.iterator();
            while (iter.hasNext() && freeQueue.size() < freeQueueSize) {
                buffer = iter.next();
                if (!buffer.isHeld()) {
                    iter.remove();
                    if (freeSet.add(buffer)) {
                        freeQueue.addLast(buffer);
                    }
                }
            }

            if (freeQueue.isEmpty()) {
                for (var page : pages) {
                    if (!page.isHeld()) {
                        if (freeSet.add(page)) {
                            freeQueue.addLast(page);
                            backingMap.remove(page.pageAddress());
                        }
                    }
                    if (freeQueue.size() >= freeQueueSize) {
                        break;
                    }
                }
                logger.warn("Ran expensive reclamation, freed {}", freeQueue.size());
            }

            var entry = freeQueue.pollFirst();
            if (entry != null) {
                freeSet.remove(entry);
            }
            return entry;
        }
        finally {
            mapLock.unlockWrite(mapStamp);
        }
    }

    public Object getFreeQueueSize() {
        long queueLock = mapLock.readLock();
        try {
            return freeQueue.size();
        }
        finally {
            mapLock.unlockRead(queueLock);
        }
    }
}
