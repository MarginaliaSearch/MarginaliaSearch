package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface DoubleColumnWriter extends ColumnWriter, AutoCloseable {
    void put(double value) throws IOException;

    void close() throws IOException;
}
