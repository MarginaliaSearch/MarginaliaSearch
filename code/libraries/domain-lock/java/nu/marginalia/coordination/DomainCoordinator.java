package nu.marginalia.coordination;

import com.google.inject.ImplementedBy;
import nu.marginalia.model.EdgeDomain;

import java.time.Duration;
import java.util.Optional;

/** Locks domains for crawler coordination.  Will also guarantee a grace period since
 * the alst access to avoid quick fire requests targetting the same domain across multiple threads
 */

@ImplementedBy(LocalDomainCoordinator.class)
public interface DomainCoordinator {
    DomainLock lockDomain(EdgeDomain domain) throws InterruptedException;

    Optional<DomainLock> tryLockDomain(EdgeDomain domain, Duration timeout) throws InterruptedException;
    Optional<DomainLock> tryLockDomain(EdgeDomain domain)  throws InterruptedException;

    boolean isLockableHint(EdgeDomain domain);
}
