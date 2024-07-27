package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class DoubleColumn {

    public static DoubleColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(Storage.reader(path, columnDesc, true));
    }

    public static DoubleColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(Storage.writer(path, columnDesc));
    }

    private static class Writer implements DoubleColumnWriter {
        private final StorageWriter storage;
        private long position = 0;

        public Writer(StorageWriter storageWriter) throws IOException {
            this.storage = storageWriter;
        }

        public void put(double value) throws IOException {
            storage.putDouble(value);
            position++;
        }

        public long position() {
            return position / Double.BYTES;
        }

        public void close() throws IOException {
            storage.close();
        }
    }

    private static class Reader implements DoubleColumnReader {
        private final StorageReader storage;

        public Reader(StorageReader storage) throws IOException {
            this.storage = storage;
        }

        public double get() throws IOException {
            return storage.getDouble();
        }

        @Override
        public long position() throws IOException {
            return storage.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Double.BYTES);
        }

        public void seek(long position) throws IOException {
            storage.seek(position, Double.BYTES);
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
