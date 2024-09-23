package nu.marginalia.crawl.logic;

import nu.marginalia.model.EdgeDomain;

import java.util.Map;
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
    public Semaphore getSemaphore(EdgeDomain domain) {
        return locks.computeIfAbsent(domain.topDomain.toLowerCase(), this::defaultPermits);
    }

    private Semaphore defaultPermits(String topDomain) {
        if (topDomain.equals("wordpress.com"))
            return new Semaphore(16);
        if (topDomain.equals("blogspot.com"))
            return new Semaphore(8);

        if (topDomain.equals("neocities.org"))
            return new Semaphore(4);
        if (topDomain.equals("github.io"))
            return new Semaphore(4);

        if (topDomain.equals("substack.com")) {
            return new Semaphore(1);
        }
        if (topDomain.endsWith(".edu")) {
            return new Semaphore(1);
        }

        return new Semaphore(2);
    }
}
