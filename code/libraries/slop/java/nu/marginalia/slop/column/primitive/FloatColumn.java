package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class FloatColumn {

    public static FloatColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(Storage.reader(path, columnDesc, true));
    }

    public static FloatColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(Storage.writer(path, columnDesc));
    }


    private static class Writer implements FloatColumnWriter {
        private final StorageWriter storage;
        private long position = 0;

        public Writer(StorageWriter storageWriter) throws IOException {
            this.storage = storageWriter;
        }

        public void put(float value) throws IOException {
            storage.putFloat(value);
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
        }
    }

    private static class Reader implements FloatColumnReader {
        private final StorageReader storage;

        public Reader(StorageReader storage) throws IOException {
            this.storage = storage;
        }

        public float get() throws IOException {
            return storage.getFloat();
        }

        @Override
        public long position() throws IOException {
            return storage.position() / Float.BYTES;
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Float.BYTES);
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
