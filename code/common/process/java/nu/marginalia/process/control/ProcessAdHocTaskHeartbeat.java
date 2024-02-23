package nu.marginalia.process.control;

public interface ProcessAdHocTaskHeartbeat extends AutoCloseable {
    void progress(String step, int progress, int total);

    void close();
}
