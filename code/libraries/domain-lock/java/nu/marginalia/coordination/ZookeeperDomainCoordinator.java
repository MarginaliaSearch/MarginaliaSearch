package nu.marginalia.coordination;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreV2;
import org.apache.curator.framework.recipes.locks.Lease;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Singleton
public class ZookeeperDomainCoordinator implements DomainCoordinator {
    // The locks are stored in a map, with the domain name as the key.  This map will grow
    // relatively big, but should be manageable since the number of domains is limited to
    // a few hundred thousand typically.
    private final Map<String, InterProcessSemaphoreV2> locks = new ConcurrentHashMap<>();
    private final Map<String, Integer> waitCounts = new ConcurrentHashMap<>();

    private final ServiceRegistryIf serviceRegistry;
    private final int nodeId;

    @Inject
    public ZookeeperDomainCoordinator(ServiceRegistryIf serviceRegistry, @Named("wmsa-system-node") int nodeId) {
        // Zookeeper-specific initialization can be done here if needed
        this.serviceRegistry = serviceRegistry;
        this.nodeId = nodeId;
    }

    /** Returns a lock object corresponding to the given domain.  The object is returned as-is,
     * and may be held by another thread.  The caller is responsible for locking and  releasing the lock.
     */
    public DomainLock lockDomain(EdgeDomain domain) throws InterruptedException {
        final String key = domain.topDomain.toLowerCase();
        var sem = locks.computeIfAbsent(key, this::createSemapore);

        // Increment or add a wait count for the domain
        waitCounts.compute(key, (k,value) -> (value == null ? 1 : value + 1));
        try {
            return new ZkDomainLock(sem, sem.acquire());
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to acquire lock for domain: " + domain.topDomain, e);
        }
        finally {
            // Decrement or remove the wait count for the domain
            waitCounts.compute(key, (k,value) -> (value == null || value <= 1) ? null : value - 1);
        }
    }


    public Optional<DomainLock> tryLockDomain(EdgeDomain domain) throws InterruptedException {
        return tryLockDomain(domain, Duration.ofSeconds(1)); // Underlying semaphore doesn't have a tryLock method, so we use a short timeout
    }


    public Optional<DomainLock> tryLockDomain(EdgeDomain domain, Duration timeout) throws InterruptedException {
        final String key = domain.topDomain.toLowerCase();
        var sem = locks.computeIfAbsent(key, this::createSemapore);

        // Increment or add a wait count for the domain
        waitCounts.compute(key, (k,value) -> (value == null ? 1 : value + 1));
        try {
            var lease = sem.acquire(timeout.toMillis(), TimeUnit.MILLISECONDS); // Acquire with timeout
            if (lease != null) {
                return Optional.of(new ZkDomainLock(sem, lease));
            }
            else {
                return Optional.empty(); // If we fail to acquire the lease, we return an empty optional
            }
        }
        catch (Exception e) {
            return Optional.empty(); // If we fail to acquire the lock, we return an empty optional
        }
        finally {
            waitCounts.compute(key, (k,value) -> (value == null || value <= 1) ? null : value - 1);
        }
    }

    private InterProcessSemaphoreV2 createSemapore(String topDomain){
        try {
            return serviceRegistry.getSemaphore(topDomain + ":" + nodeId, DefaultDomainPermits.defaultPermits(topDomain));
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to get semaphore for domain: " + topDomain, e);
        }
    }

    /** Returns true if the domain is lockable, i.e. if it is not already locked by another thread.
     * (this is just a hint, and does not guarantee that the domain is actually lockable any time
     * after this method returns true)
     */
    public boolean isLockableHint(EdgeDomain domain) {
        return !waitCounts.containsKey(domain.topDomain.toLowerCase());
    }

    public static class ZkDomainLock implements DomainLock {
        private final InterProcessSemaphoreV2 semaphore;
        private final Lease lease;

        ZkDomainLock(InterProcessSemaphoreV2 semaphore, Lease lease) {
            this.semaphore = semaphore;
            this.lease = lease;
        }

        @Override
        public void close() {
            semaphore.returnLease(lease);
        }
    }
}
