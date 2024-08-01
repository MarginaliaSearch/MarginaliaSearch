package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ObjectColumnWriter;

import java.io.IOException;

public interface IntArrayColumnWriter extends ObjectColumnWriter<int[]>, AutoCloseable {
    void put(int[] value) throws IOException;

    void close() throws IOException;
}
