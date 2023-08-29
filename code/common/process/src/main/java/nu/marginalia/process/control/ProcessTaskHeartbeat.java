package nu.marginalia.process.control;

public interface ProcessTaskHeartbeat<T extends Enum<T>> extends AutoCloseable {
    void progress(T step);

    void shutDown();

    void close();
}
