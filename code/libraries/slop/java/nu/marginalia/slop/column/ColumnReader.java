package nu.marginalia.slop.column;

import nu.marginalia.slop.desc.ColumnDesc;

import java.io.IOException;

public interface ColumnReader {

    ColumnDesc<?, ?> columnDesc();

    long position() throws IOException;
    void skip(long positions) throws IOException;

    boolean hasRemaining() throws IOException;

    void close() throws IOException;
}
