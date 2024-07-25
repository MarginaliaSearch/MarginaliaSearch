package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnReader;

import java.io.IOException;

public interface DoubleColumnReader extends ColumnReader, AutoCloseable {
    double get() throws IOException;
    void close() throws IOException;
}
