package nu.marginalia.coordination;

import nu.marginalia.model.EdgeDomain;

import java.time.Duration;
import java.util.Optional;

public interface DomainCoordinator {
    DomainLock lockDomain(EdgeDomain domain) throws InterruptedException;
    Optional<DomainLock> tryLockDomain(EdgeDomain domain, Duration timeout) throws InterruptedException;
    Optional<DomainLock> tryLockDomain(EdgeDomain domain)  throws InterruptedException;
    boolean isLockableHint(EdgeDomain domain);
}
