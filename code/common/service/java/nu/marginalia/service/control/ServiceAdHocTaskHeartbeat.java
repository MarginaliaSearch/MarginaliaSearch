package nu.marginalia.service.control;

public interface ServiceAdHocTaskHeartbeat extends AutoCloseable {
    void progress(String step, int progress, int total);

    void close();
}
