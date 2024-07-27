package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class CharColumn {

    public static CharColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(Storage.reader(path, columnDesc, true));
    }

    public static CharColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(Storage.writer(path, columnDesc));
    }

    private static class Writer implements CharColumnWriter {
        private final StorageWriter storage;
        private long position = 0;

        public Writer(StorageWriter storageWriter) throws IOException {
            this.storage = storageWriter;
        }

        public void put(char value) throws IOException {
            storage.putChar(value);
            position++;
        }

        public long position() {
            return position / Character.BYTES;
        }

        public void close() throws IOException {
            storage.close();
        }
    }

    private static class Reader implements CharColumnReader {
        private final StorageReader storage;

        public Reader(StorageReader storage) throws IOException {
            this.storage = storage;
        }

        public char get() throws IOException {
            return storage.getChar();
        }

        @Override
        public long position() throws IOException {
            return storage.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Character.BYTES);
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
