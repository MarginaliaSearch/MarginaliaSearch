package nu.marginalia.slop.column;

import nu.marginalia.slop.desc.ColumnDesc;

import java.io.IOException;

public interface ColumnWriter {
    ColumnDesc<?, ?> columnDesc();
    
    /** Return the current record index in the column */
    long position();

    void close() throws IOException;
}
