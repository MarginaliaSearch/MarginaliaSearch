package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnReader;

import java.io.IOException;

public interface LongColumnReader extends ColumnReader, AutoCloseable {
    long get() throws IOException;
    void close() throws IOException;
}
