package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class IntColumn {

    public static IntColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(columnDesc, Storage.reader(path, columnDesc, true));
    }

    public static IntColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(columnDesc, Storage.writer(path, columnDesc));
    }

    private static class Writer implements IntColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageWriter storage;
        private long position = 0;

        public Writer(ColumnDesc<?, ?> columnDesc, StorageWriter storageWriter) throws IOException {
            this.columnDesc = columnDesc;
            this.storage = storageWriter;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(int[] values) throws IOException {
            for (int value : values) {
                storage.putInt(value);
            }
            position+=values.length;
        }

        public void put(int value) throws IOException {
            storage.putInt(value);
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
        }
    }

    private static class Reader implements IntColumnReader {
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

        public int get() throws IOException {
            return storage.getInt();
        }

        @Override
        public long position() throws IOException {
            return storage.position() / Integer.BYTES;
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Integer.BYTES);
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
