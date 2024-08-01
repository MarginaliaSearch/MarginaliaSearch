package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnReader;

import java.io.IOException;

public interface ByteColumnReader extends ColumnReader, AutoCloseable {
    byte get() throws IOException;
    void close() throws IOException;
}
