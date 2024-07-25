package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface LongColumnWriter extends ColumnWriter, AutoCloseable {
    void put(long value) throws IOException;
    void close() throws IOException;
}
