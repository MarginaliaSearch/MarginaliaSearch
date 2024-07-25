package nu.marginalia.slop.column.dynamic;

import nu.marginalia.slop.column.primitive.LongColumnReader;

import java.io.IOException;

public interface VarintColumnReader extends LongColumnReader {

    @Override
    long position() throws IOException;

    @Override
    void skip(long positions) throws IOException;

    @Override
    boolean hasRemaining() throws IOException;
}
