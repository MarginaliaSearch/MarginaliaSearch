package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface LongArrayColumnWriter  extends ColumnWriter, AutoCloseable {
    void put(long[] value) throws IOException;

    void close() throws IOException;
}
