package nu.marginalia.service.control;

public interface ServiceTaskHeartbeat<T extends Enum<T>> extends AutoCloseable {
    void progress(T step);

    @Override
    void close();
}
