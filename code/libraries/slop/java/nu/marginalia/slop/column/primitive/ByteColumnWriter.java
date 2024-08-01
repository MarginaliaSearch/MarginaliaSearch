package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface ByteColumnWriter extends ColumnWriter, AutoCloseable {
    void put(byte value) throws IOException;

    void close() throws IOException;
}
