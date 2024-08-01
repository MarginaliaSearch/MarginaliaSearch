package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ObjectColumnWriter;

import java.io.IOException;
import java.util.List;

public interface ObjectArrayColumnWriter<T> extends ObjectColumnWriter<List<T>>, AutoCloseable {
    void put(List<T> values) throws IOException;

    void close() throws IOException;
}
