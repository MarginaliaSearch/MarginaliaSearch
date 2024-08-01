package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnReader;

import java.io.IOException;

public interface FloatColumnReader extends ColumnReader, AutoCloseable {
    float get() throws IOException;
    void close() throws IOException;
}
