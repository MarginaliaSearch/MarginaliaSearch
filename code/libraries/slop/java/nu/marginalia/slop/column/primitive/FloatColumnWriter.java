package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface FloatColumnWriter extends ColumnWriter, AutoCloseable {
    void put(float value) throws IOException;

    void close() throws IOException;
}
