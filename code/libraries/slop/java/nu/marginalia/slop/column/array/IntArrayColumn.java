package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.column.dynamic.VarintColumnReader;
import nu.marginalia.slop.column.dynamic.VarintColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class IntArrayColumn {

    public static IntArrayColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(columnDesc,
                Storage.reader(path, columnDesc, true),
                VarintColumn.open(path, columnDesc.createSupplementaryColumn(ColumnFunction.DATA_LEN, ColumnType.VARINT_LE, StorageType.PLAIN))
        );
    }

    public static IntArrayColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(columnDesc,
                Storage.writer(path, columnDesc),
                VarintColumn.create(path, columnDesc.createSupplementaryColumn(ColumnFunction.DATA_LEN, ColumnType.VARINT_LE, StorageType.PLAIN))
        );
    }

    public static ObjectArrayColumnReader<int[]> openNested(Path path, ColumnDesc desc) throws IOException {
        return ObjectArrayColumn.open(path, desc, open(path, desc));
    }

    public static ObjectArrayColumnWriter<int[]> createNested(Path path, ColumnDesc desc) throws IOException {
        return ObjectArrayColumn.create(path, desc, create(path, desc));
    }

    private static class Writer implements IntArrayColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageWriter storage;
        private final VarintColumnWriter lengthsWriter;

        public Writer(ColumnDesc<?, ?> columnDesc, StorageWriter storage, VarintColumnWriter lengthsWriter) throws IOException {
            this.columnDesc = columnDesc;
            this.storage = storage;
            this.lengthsWriter = lengthsWriter;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(int[] value) throws IOException {
            storage.putInts(value);
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

    private static class Reader implements IntArrayColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageReader storage;
        private final VarintColumnReader lengthsReader;

        public Reader(ColumnDesc<?, ?> columnDesc, StorageReader storage, VarintColumnReader lengthsReader) {
            this.columnDesc = columnDesc;
            this.storage = storage;
            this.lengthsReader = lengthsReader;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public int[] get() throws IOException {
            int length = (int) lengthsReader.get();
            int[] ret = new int[length];
            storage.getInts(ret);
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
                storage.skip(size, Integer.BYTES);
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
