package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnReader;

import java.io.IOException;

public interface CharColumnReader extends ColumnReader, AutoCloseable {
    char get() throws IOException;
    void close() throws IOException;
}
