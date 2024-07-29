package nu.marginalia.slop.column;

import nu.marginalia.slop.desc.ColumnDesc;

import java.io.IOException;

public interface ObjectColumnWriter<T> extends ColumnWriter {
    ColumnDesc<?, ?> columnDesc();

    void put(T value) throws IOException;

    /** Return the current record index in the column */
    long position();

    void close() throws IOException;
}
