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
    public DomainLock lockDomain(EdgeDomain domain) throws InterruptedException {
        return new DomainLock(domain.toString(),
                locks.computeIfAbsent(domain.topDomain.toLowerCase(), this::defaultPermits));
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

    public boolean canLock(EdgeDomain domain) {
        Semaphore sem = locks.get(domain.topDomain.toLowerCase());
        if (null == sem)
            return true;
        else
            return sem.availablePermits() > 0;
    }

    public static class DomainLock implements AutoCloseable {
        private final String domainName;
        private final Semaphore semaphore;

        DomainLock(String domainName, Semaphore semaphore) throws InterruptedException {
            this.domainName = domainName;
            this.semaphore = semaphore;

            Thread.currentThread().setName("crawling:" + domainName + " [await domain lock]");
            semaphore.acquire();
            Thread.currentThread().setName("crawling:" + domainName);
        }

        @Override
        public void close() throws Exception {
            semaphore.release();
            Thread.currentThread().setName("crawling:" + domainName + " [wrapping up]");
        }
    }
}
