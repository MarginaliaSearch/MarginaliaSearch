package nu.marginalia.slop.column.primitive;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class ShortColumn {

    public static ShortColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(columnDesc, Storage.reader(path, columnDesc, true));
    }

    public static ShortColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(columnDesc, Storage.writer(path, columnDesc));
    }

    private static class Writer implements ShortColumnWriter {
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

        public void put(short value) throws IOException {
            storage.putShort(value);
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
        }
    }

    private static class Reader implements ShortColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageReader storage;

        public Reader(ColumnDesc<?, ?> columnDesc, StorageReader storage) throws IOException {
            this.columnDesc = columnDesc;
            this.storage = storage;
        }

        public short get() throws IOException {
            return storage.getShort();
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        @Override
        public long position() throws IOException {
            return storage.position() / Short.BYTES;
        }

        @Override
        public void skip(long positions) throws IOException {
            storage.skip(positions, Short.BYTES);
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
