package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface CharColumnWriter extends ColumnWriter, AutoCloseable {
    void put(char value) throws IOException;

    void close() throws IOException;
}
