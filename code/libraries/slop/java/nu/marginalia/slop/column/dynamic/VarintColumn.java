package nu.marginalia.slop.column.dynamic;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class VarintColumn {

    public static VarintColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(Storage.reader(path, columnDesc, true));
    }

    public static VarintColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(Storage.writer(path, columnDesc));
    }


    private static class Writer implements VarintColumnWriter {
        private final StorageWriter writer;

        public Writer(StorageWriter writer) throws IOException {
            this.writer = writer;
        }

        public void put(long value) throws IOException {
            while ((value & ~0x7F) != 0) {
                writer.putByte((byte) (0x80 | (value & 0x7F)));
                value >>>= 7;
            }
            writer.putByte((byte) (value & 0x7F));
        }

        public void put(long[] values) throws IOException {
            for (long val : values) {
                put(val);
            }
        }

        public void close() throws IOException {
            writer.close();
        }
    }

    private static class Reader implements VarintColumnReader {
        private final StorageReader reader;

        private long position = 0;

        public Reader(StorageReader reader) throws IOException {
            this.reader = reader;
        }

        public long get() throws IOException {
            long value = 0;
            int shift = 0;

            while (true) {
                long b = reader.getByte();
                value |= (b & 0x7F) << shift;
                shift += 7;
                if ((b & 0x80) == 0) {
                    break;
                }
            }

            position++;

            return value;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public void skip(long positions) throws IOException {
            for (long i = 0; i < positions; i++) {
                get();
            }
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return reader.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

}
