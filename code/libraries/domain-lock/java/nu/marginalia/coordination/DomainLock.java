package nu.marginalia.coordination;

public interface DomainLock extends AutoCloseable {
    void close();
}
