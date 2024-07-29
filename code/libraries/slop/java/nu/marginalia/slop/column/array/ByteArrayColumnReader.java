package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ObjectColumnReader;

import java.io.IOException;

public interface ByteArrayColumnReader extends ObjectColumnReader<byte[]>, AutoCloseable {
    byte[] get() throws IOException;
    void close() throws IOException;


    @Override
    long position() throws IOException;

    @Override
    void skip(long positions) throws IOException;

    @Override
    boolean hasRemaining() throws IOException;
}
