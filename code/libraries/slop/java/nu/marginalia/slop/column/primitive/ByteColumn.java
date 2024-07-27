package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class ByteColumn {

    public static ByteColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(Storage.reader(path, columnDesc, true));
    }

    public static ByteColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(Storage.writer(path, columnDesc));
    }

    private static class Writer implements ByteColumnWriter {
        private final StorageWriter storage;
        private long position = 0;

        public Writer(StorageWriter storageWriter) throws IOException {
            this.storage = storageWriter;
        }

        public void put(byte value) throws IOException {
            storage.putByte(value);
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
        }
    }

    private static class Reader implements ByteColumnReader {
        private final StorageReader storage;

        public Reader(StorageReader storage) throws IOException {
            this.storage = storage;
        }

        public byte get() throws IOException {
            return storage.getByte();
        }

        @Override
        public long position() throws IOException {
            return storage.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Byte.BYTES);
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
