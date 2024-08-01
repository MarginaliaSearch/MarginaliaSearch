package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.ObjectColumnReader;

import java.io.IOException;

public interface StringColumnReader extends ObjectColumnReader<String>, AutoCloseable {

    String get() throws IOException;

    @Override
    long position() throws IOException;

    @Override
    void skip(long positions) throws IOException;

    @Override
    boolean hasRemaining() throws IOException;

    @Override
    void close() throws IOException;
}
