package nu.marginalia.wmsa.edge.crawler.domain;

import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.wmsa.edge.model.WideHashable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class UrlsCache<T extends WideHashable> {
    private final TLongHashSet _int_set_not_thread_safe = new TLongHashSet();
    private final long[] _inserts_not_thread_safe;

    private int insertP = 0;
    private long size = 0;
    private final int maxSize;

    private final AtomicBoolean spinLock = new AtomicBoolean();

    public UrlsCache() {
        this(50000);
    }

    public UrlsCache(final int maxSize) {
        this.maxSize = maxSize;
        _inserts_not_thread_safe = new long[maxSize];
    }

    /**
     *
     * @return true if the set was modified
     */
    public boolean add(T entity) {
        try {
            while (!spinLock.compareAndSet(false, true));

            return addEntityThreadUnsafe(entity.wideHash());
        }
        finally {
            spinLock.set(false);
        }
    }

    private boolean addEntityThreadUnsafe(long hash) {

        if (!_int_set_not_thread_safe.add(hash)) {
            return false;
        }

        if (size == maxSize) {
            _int_set_not_thread_safe.remove(_inserts_not_thread_safe[insertP]);
        }
        else {
            size++;
        }

        _inserts_not_thread_safe[insertP] = hash;
        insertP = (insertP+1) % maxSize;

        return true;
    }

    public void addAll(T... entities) {
        try {
            while (!spinLock.compareAndSet(false, true));

            Arrays.stream(entities)
                  .mapToLong(WideHashable::wideHash)
                  .forEach(this::addEntityThreadUnsafe);
        }
        finally {
            spinLock.set(false);
        }
    }

    public boolean contains(T entity) {
        try {
            while (!spinLock.compareAndSet(false, true));

            return _int_set_not_thread_safe.contains(entity.wideHash());
        }
        finally {
            spinLock.set(false);
        }
    }

    public boolean isMissing(T entity) {
        try {
            while (!spinLock.compareAndSet(false, true));

            return !_int_set_not_thread_safe.contains(entity.wideHash());
        }
        finally {
            spinLock.set(false);
        }
    }


    public void clear() {
        try {
            while (!spinLock.compareAndSet(false, true)) ;
            _int_set_not_thread_safe.clear();
            size = 0;
            insertP = 0;
        }
        finally {
            spinLock.set(false);
        }
    }

}
