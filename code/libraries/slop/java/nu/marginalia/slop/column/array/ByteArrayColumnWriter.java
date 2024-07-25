package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface ByteArrayColumnWriter extends ColumnWriter, AutoCloseable {
    void put(byte[] value) throws IOException;

    void close() throws IOException;
}
