package nu.marginalia.coordination;

import com.google.inject.ImplementedBy;

@ImplementedBy(LocalDomainCoordinator.LocalDomainLock.class)
public interface DomainLock extends AutoCloseable {
    void close();
}
