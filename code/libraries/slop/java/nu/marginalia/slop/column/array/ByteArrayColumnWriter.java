package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ObjectColumnWriter;

import java.io.IOException;

public interface ByteArrayColumnWriter extends ObjectColumnWriter<byte[]>, AutoCloseable {
    void put(byte[] value) throws IOException;

    void close() throws IOException;
}
