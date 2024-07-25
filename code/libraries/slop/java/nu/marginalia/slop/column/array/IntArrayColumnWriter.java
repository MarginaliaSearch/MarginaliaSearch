package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface IntArrayColumnWriter  extends ColumnWriter, AutoCloseable {
    void put(int[] value) throws IOException;

    void close() throws IOException;
}
