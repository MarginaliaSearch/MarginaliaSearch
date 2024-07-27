package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class LongColumn {

    public static LongColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(columnDesc, Storage.reader(path, columnDesc, true));
    }

    public static LongColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(columnDesc, Storage.writer(path, columnDesc));
    }

    private static class Writer implements LongColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageWriter storage;
        private long position = 0;

        public Writer(ColumnDesc<?, ?> columnDesc, StorageWriter storageWriter) {
            this.columnDesc = columnDesc;
            this.storage = storageWriter;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(long value) throws IOException {
            storage.putLong(value);
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
        }
    }

    private static class Reader implements LongColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageReader storage;

        public Reader(ColumnDesc<?, ?> columnDesc, StorageReader storage) throws IOException {
            this.columnDesc = columnDesc;
            this.storage = storage;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public long get() throws IOException {
            return storage.getLong();
        }

        @Override
        public long position() throws IOException {
            return storage.position() / Long.BYTES;
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Long.BYTES);
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return storage.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            storage.close();
        }
    }

}
