package nu.marginalia.coordination;

import com.google.inject.Singleton;
import nu.marginalia.model.EdgeDomain;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Singleton
public class LocalDomainCoordinator implements DomainCoordinator {
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

        return new LocalDomainLock(sem);
    }

    public Optional<DomainLock> tryLockDomain(EdgeDomain domain) {
        var sem = locks.computeIfAbsent(domain.topDomain.toLowerCase(), this::defaultPermits);
        if (sem.tryAcquire(1)) {
            return Optional.of(new LocalDomainLock(sem));
        }
        else {
            // We don't have a lock, so we return an empty optional
            return Optional.empty();
        }
    }


    public Optional<DomainLock> tryLockDomain(EdgeDomain domain, Duration timeout) throws InterruptedException {
        var sem = locks.computeIfAbsent(domain.topDomain.toLowerCase(), this::defaultPermits);
        if (sem.tryAcquire(1, timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            return Optional.of(new LocalDomainLock(sem));
        }
        else {
            // We don't have a lock, so we return an empty optional
            return Optional.empty();
        }
    }

    private Semaphore defaultPermits(String topDomain) {
        return new Semaphore(DefaultDomainPermits.defaultPermits(topDomain));
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

    public static class LocalDomainLock implements DomainLock {
        private final Semaphore semaphore;

        LocalDomainLock(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            semaphore.release();
            Thread.currentThread().setName("[idle]");
        }
    }
}
