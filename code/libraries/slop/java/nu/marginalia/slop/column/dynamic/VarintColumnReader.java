package nu.marginalia.slop.column.dynamic;

import nu.marginalia.slop.column.primitive.IntColumnReader;

import java.io.IOException;

public interface VarintColumnReader extends IntColumnReader {

    int get() throws IOException;
    long getLong() throws IOException;

    @Override
    long position() throws IOException;

    @Override
    void skip(long positions) throws IOException;

    @Override
    boolean hasRemaining() throws IOException;
}
