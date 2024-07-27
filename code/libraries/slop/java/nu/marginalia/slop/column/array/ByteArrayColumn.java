package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.column.dynamic.VarintColumnReader;
import nu.marginalia.slop.column.dynamic.VarintColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class ByteArrayColumn {

    public static ByteArrayColumnReader open(Path path, ColumnDesc name) throws IOException {
        return new Reader(
                Storage.reader(path, name, true),
                VarintColumn.open(path,
                        name.createSupplementaryColumn(name.function().lengthsTable(),
                                ColumnType.VARINT_LE,
                                StorageType.PLAIN)
                )
        );
    }

    public static ByteArrayColumnWriter create(Path path, ColumnDesc name) throws IOException {
        return new Writer(
                Storage.writer(path, name),
                VarintColumn.create(path,
                        name.createSupplementaryColumn(name.function().lengthsTable(),
                                ColumnType.VARINT_LE,
                                StorageType.PLAIN)
                )
        );
    }

    private static class Writer implements ByteArrayColumnWriter {
        private final StorageWriter storage;
        private final VarintColumnWriter lengthsWriter;

        private long position = 0;

        public Writer(StorageWriter storage, VarintColumnWriter lengthsWriter) throws IOException {
            this.storage = storage;
            this.lengthsWriter = lengthsWriter;
        }

        public void put(byte[] value) throws IOException {
            position ++;
            storage.putBytes(value);
            lengthsWriter.put(value.length);
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storage.close();
            lengthsWriter.close();
        }
    }

    private static class Reader implements ByteArrayColumnReader {
        private final StorageReader storage;
        private final VarintColumnReader lengthsReader;

        public Reader(StorageReader storage, VarintColumnReader lengthsReader) throws IOException {
            this.storage = storage;
            this.lengthsReader = lengthsReader;
        }

        public byte[] get() throws IOException {
            int length = (int) lengthsReader.get();
            byte[] ret = new byte[length];
            storage.getBytes(ret);
            return ret;
        }

        @Override
        public long position() throws IOException {
            return lengthsReader.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            for (int i = 0; i < positions; i++) {
                int size = (int) lengthsReader.get();
                storage.skip(size, 1);
            }
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return lengthsReader.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            storage.close();
            lengthsReader.close();
        }
    }

}
