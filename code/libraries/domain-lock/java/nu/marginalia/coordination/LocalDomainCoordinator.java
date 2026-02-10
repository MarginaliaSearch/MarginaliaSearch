package nu.marginalia.coordination;

import com.google.inject.Singleton;
import nu.marginalia.model.EdgeDomain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Singleton
public class LocalDomainCoordinator implements DomainCoordinator {
    // The data is stored in a map, with the domain name as the key.  This map will grow
    // relatively big, but should be manageable since the number of domains is limited to
    // a few hundred thousand typically

    private final Map<String, Semaphore> locks = new ConcurrentHashMap<>();
    private final Map<String, Instant> accessTimes = new ConcurrentHashMap<>();

    private final Duration requestCadence = Duration.ofSeconds(1);

    /** Returns a lock object corresponding to the given domain.  The object is returned as-is,
     * and may be held by another thread.  The caller is responsible for locking and  releasing the lock.
     */
    public DomainLock lockDomain(EdgeDomain domain) throws InterruptedException {
        String key = domain.topDomain.toLowerCase();

        var sem = locks.computeIfAbsent(key, this::defaultPermits);

        sem.acquire();

        Thread.sleep(timeUntilNextRequest(key));

        return new LocalDomainLock(key, sem);
    }

    public Optional<DomainLock> tryLockDomain(EdgeDomain domain) {
        String key = domain.topDomain.toLowerCase();

        var sem = locks.computeIfAbsent(key, this::defaultPermits);
        if (sem.tryAcquire(1)) {

            if (timeUntilNextRequest(key).isPositive()) {
                sem.release();
                return Optional.empty();
            }

            return Optional.of(new LocalDomainLock(key, sem));
        }
        else {
            // We don't have a lock, so we return an empty optional
            return Optional.empty();
        }
    }


    public Optional<DomainLock> tryLockDomain(EdgeDomain domain, Duration timeout) throws InterruptedException {
        String key = domain.topDomain.toLowerCase();

        Instant cutoffTime = Instant.now().plus(timeout);

        var sem = locks.computeIfAbsent(key, this::defaultPermits);
        if (sem.tryAcquire(1, timeout.toMillis(), TimeUnit.MILLISECONDS)) {

            Duration timeUntilNextRequest = timeUntilNextRequest(key);
            Duration timeRemaining = Duration.between(Instant.now(), cutoffTime);

            if (timeRemaining.isNegative() || timeRemaining.compareTo(timeUntilNextRequest) < 0) {
                sem.release();
                return Optional.empty();
            }

            Thread.sleep(timeUntilNextRequest);

            return Optional.of(new LocalDomainLock(key, sem));
        }
        else {
            // We don't have a lock, so we return an empty optional
            return Optional.empty();
        }
    }

    private Semaphore defaultPermits(String topDomain) {
        return new Semaphore(DefaultDomainPermits.defaultPermits(topDomain));
    }

    private Instant nextPermissibleRequestTime(String key) {
        return accessTimes
                .getOrDefault(key, Instant.EPOCH)
                .plus(requestCadence);
    }

    private Duration timeUntilNextRequest(String key) {
        return Duration.between(Instant.now(), nextPermissibleRequestTime(key));
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

    public class LocalDomainLock implements DomainLock {
        private final String key;
        private final Semaphore semaphore;

        LocalDomainLock(String key, Semaphore semaphore) {
            this.key = key;
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            accessTimes.put(key, Instant.now()); // order is important here
            semaphore.release();
        }
    }
}
