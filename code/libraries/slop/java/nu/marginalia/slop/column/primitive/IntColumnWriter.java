package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.column.ColumnWriter;

import java.io.IOException;

public interface IntColumnWriter extends ColumnWriter, AutoCloseable {
    void put(int value) throws IOException;
    void put(int[] values) throws IOException;


    void close() throws IOException;
}
