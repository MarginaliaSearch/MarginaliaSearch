package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.ColumnReader;

import java.io.IOException;

public interface StringColumnReader extends ColumnReader, AutoCloseable {

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
