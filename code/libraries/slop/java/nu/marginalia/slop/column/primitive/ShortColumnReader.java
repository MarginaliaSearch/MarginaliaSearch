package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnReader;

import java.io.IOException;

public interface ShortColumnReader extends ColumnReader, AutoCloseable {
    short get() throws IOException;
    void close() throws IOException;
}
