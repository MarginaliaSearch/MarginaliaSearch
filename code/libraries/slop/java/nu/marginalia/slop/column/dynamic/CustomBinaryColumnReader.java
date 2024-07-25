package nu.marginalia.slop.column.dynamic;

import nu.marginalia.slop.column.ColumnReader;
import nu.marginalia.slop.storage.StorageReader;

import java.io.IOException;

public interface CustomBinaryColumnReader extends ColumnReader, AutoCloseable {
    RecordReader next() throws IOException;
    void close() throws IOException;

    interface RecordReader extends AutoCloseable {
        int size();
        StorageReader reader();
        void close() throws IOException;
    }
}
