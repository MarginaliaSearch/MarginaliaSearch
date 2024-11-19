package nu.marginalia.process.control;

import java.util.Collection;

public interface ProcessAdHocTaskHeartbeat extends AutoCloseable {
    void progress(String step, int progress, int total);

    /** Wrap a collection to provide heartbeat progress updates as it's iterated through */
    <T> Iterable<T> wrap(String step, Collection<T> collection);

    void close();
}
