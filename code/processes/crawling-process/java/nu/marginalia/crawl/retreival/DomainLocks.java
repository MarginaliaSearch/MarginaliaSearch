package nu.marginalia.crawl.retreival;

import nu.marginalia.model.EdgeDomain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Holds lock objects for each domain, to prevent multiple threads from
 * crawling the same domain at the same time.
 */
public class DomainLocks {
    // The locks are stored in a map, with the domain name as the key.  This map will grow
    // relatively big, but should be manageable since the number of domains is limited to
    // a few hundred thousand typically.
    private final Map<String, Lock> locks = new ConcurrentHashMap<>();

    /** Returns a lock object corresponding to the given domain.  The object is returned as-is,
     * and may be held by another thread.  The caller is responsible for locking and  releasing the lock.
     */
    public Lock getLock(EdgeDomain domain) {
        return locks.computeIfAbsent(domain.topDomain.toLowerCase(), k -> new ReentrantLock());
    }
}
