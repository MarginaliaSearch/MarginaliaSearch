package nu.marginalia.slop.column.dynamic;

import nu.marginalia.slop.column.ColumnWriter;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;

public interface CustomBinaryColumnWriter extends ColumnWriter {
    RecordWriter next() throws IOException;
    void close() throws IOException;

    interface RecordWriter extends AutoCloseable {
        StorageWriter writer();
        void close() throws IOException;
    }
}
