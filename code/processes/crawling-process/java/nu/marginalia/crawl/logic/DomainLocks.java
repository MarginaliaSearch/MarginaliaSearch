package nu.marginalia.crawl.logic;

import nu.marginalia.model.EdgeDomain;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/** Holds lock objects for each domain, to prevent multiple threads from
 * crawling the same domain at the same time.
 */
public class DomainLocks {
    // The locks are stored in a map, with the domain name as the key.  This map will grow
    // relatively big, but should be manageable since the number of domains is limited to
    // a few hundred thousand typically.
    private final Map<String, Semaphore> locks = new ConcurrentHashMap<>();

    /** Returns a lock object corresponding to the given domain.  The object is returned as-is,
     * and may be held by another thread.  The caller is responsible for locking and  releasing the lock.
     */
    public DomainLock lockDomain(EdgeDomain domain) throws InterruptedException {
        var sem = locks.computeIfAbsent(domain.topDomain.toLowerCase(), this::defaultPermits);

        sem.acquire();

        return new DomainLock(sem);
    }

    public Optional<DomainLock> tryLockDomain(EdgeDomain domain) {
        var sem = locks.computeIfAbsent(domain.topDomain.toLowerCase(), this::defaultPermits);
        if (sem.tryAcquire(1)) {
            return Optional.of(new DomainLock(sem));
        }
        else {
            // We don't have a lock, so we return an empty optional
            return Optional.empty();
        }
    }

    private Semaphore defaultPermits(String topDomain) {
        if (topDomain.equals("wordpress.com"))
            return new Semaphore(16);
        if (topDomain.equals("blogspot.com"))
            return new Semaphore(8);
        if (topDomain.equals("tumblr.com"))
            return new Semaphore(8);
        if (topDomain.equals("neocities.org"))
            return new Semaphore(8);
        if (topDomain.equals("github.io"))
            return new Semaphore(8);

        // Substack really dislikes broad-scale crawlers, so we need to be careful
        // to not get blocked.
        if (topDomain.equals("substack.com")) {
            return new Semaphore(1);
        }

        return new Semaphore(2);
    }

    /** Returns true if the domain is lockable, i.e. if it is not already locked by another thread.
     * (this is just a hint, and does not guarantee that the domain is actually lockable any time
     * after this method returns true)
     */
    public boolean isLockableHint(EdgeDomain domain) {
        Semaphore sem = locks.get(domain.topDomain.toLowerCase());
        if (null == sem)
            return true;
        else
            return sem.availablePermits() > 0;
    }

    public static class DomainLock implements AutoCloseable {
        private final Semaphore semaphore;

        DomainLock(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() throws Exception {
            semaphore.release();
            Thread.currentThread().setName("[idle]");
        }
    }
}
