package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ObjectColumnWriter;

import java.io.IOException;

public interface LongArrayColumnWriter  extends ObjectColumnWriter<long[]>, AutoCloseable {
    void put(long[] value) throws IOException;

    void close() throws IOException;
}
