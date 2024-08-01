package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnReader;

import java.io.IOException;

public interface IntColumnReader extends ColumnReader, AutoCloseable {
    int get() throws IOException;
    void close() throws IOException;
}
