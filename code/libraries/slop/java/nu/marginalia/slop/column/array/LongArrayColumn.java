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

public class LongArrayColumn {

    public static LongArrayColumnReader open(Path path, ColumnDesc name) throws IOException {
        return new LongArrayColumn.Reader(Storage.reader(path, name, true),
                VarintColumn.open(path, name.createSupplementaryColumn(name.function().lengthsTable(),
                        ColumnType.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    public static LongArrayColumnWriter create(Path path, ColumnDesc name) throws IOException {
        return new LongArrayColumn.Writer(Storage.writer(path, name),
                VarintColumn.create(path, name.createSupplementaryColumn(name.function().lengthsTable(),
                        ColumnType.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    private static class Writer implements LongArrayColumnWriter {
        private final StorageWriter storage;
        private final VarintColumnWriter lengthsWriter;

        public Writer(StorageWriter storage, VarintColumnWriter lengthsWriter) throws IOException {
            this.storage = storage;
            this.lengthsWriter = lengthsWriter;
        }

        public void put(long[] value) throws IOException {
            storage.putLongs(value);
            lengthsWriter.put(value.length);
        }

        public long position() {
            return lengthsWriter.position();
        }

        public void close() throws IOException {
            storage.close();
            lengthsWriter.close();
        }
    }

    private static class Reader implements LongArrayColumnReader {
        private final StorageReader storage;
        private final VarintColumnReader lengthsReader;

        public Reader(StorageReader storage, VarintColumnReader lengthsReader) {
            this.storage = storage;
            this.lengthsReader = lengthsReader;
        }

        public long[] get() throws IOException {
            int length = (int) lengthsReader.get();
            long[] ret = new long[length];
            storage.getLongs(ret);
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
                storage.skip(size, Long.BYTES);
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
