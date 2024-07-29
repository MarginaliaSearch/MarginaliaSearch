package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ObjectColumnReader;

import java.io.IOException;
import java.util.List;

public interface ObjectArrayColumnReader<T> extends ObjectColumnReader<List<T>>, AutoCloseable {
    List<T> get() throws IOException;
    void close() throws IOException;


    @Override
    long position() throws IOException;

    @Override
    void skip(long positions) throws IOException;

    @Override
    boolean hasRemaining() throws IOException;
}
