package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface ShortColumnWriter extends ColumnWriter, AutoCloseable {
    void put(short value) throws IOException;

    void close() throws IOException;
}
