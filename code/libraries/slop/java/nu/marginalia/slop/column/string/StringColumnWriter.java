package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface StringColumnWriter extends ColumnWriter, AutoCloseable {
    void put(String value) throws IOException;

    @Override
    void close() throws IOException;
}
