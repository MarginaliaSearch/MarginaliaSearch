package nu.marginalia.slop.column;

import java.io.IOException;

public interface ColumnReader {
    long position() throws IOException;
    void skip(long positions) throws IOException;

    default void seek(long position) throws IOException {
        throw new UnsupportedOperationException("Random access is not supported by " + getClass().getSimpleName());
    }

    boolean hasRemaining() throws IOException;

    void close() throws IOException;
}
