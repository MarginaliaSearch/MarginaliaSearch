package nu.marginalia.slop.column.dynamic;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class VarintColumn {

    public static VarintColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        if (columnDesc.byteOrder() == ByteOrder.BIG_ENDIAN) {
            return new ReaderBE(columnDesc, Storage.reader(path, columnDesc, true));
        }
        else {
            return new ReaderLE(columnDesc, Storage.reader(path, columnDesc, true));
        }

    }

    public static VarintColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        if (columnDesc.byteOrder() == ByteOrder.BIG_ENDIAN) {
            return new WriterBE(columnDesc, Storage.writer(path, columnDesc));
        } else {
            return new WriterLE(columnDesc, Storage.writer(path, columnDesc));
        }
    }


    private static class WriterBE implements VarintColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageWriter writer;
        private long position = 0;

        public WriterBE(ColumnDesc<?,?> columnDesc, StorageWriter writer) throws IOException {
            this.columnDesc = columnDesc;
            this.writer = writer;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(long value) throws IOException {
            position++;

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

        public long position() {
            return position;
        }

        public void close() throws IOException {
            writer.close();
        }
    }

    private static class WriterLE implements VarintColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageWriter writer;
        private long position = 0;

        public WriterLE(ColumnDesc<?,?> columnDesc, StorageWriter writer) throws IOException {
            this.columnDesc = columnDesc;
            this.writer = writer;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(long value) throws IOException {
            position++;

            if (value < 0)
                throw new IllegalArgumentException("Value must be positive");

            if (value < (1<<7)) {
                writer.putByte((byte) value);
            }
            else if (value < (1<<14)) {
                writer.putByte((byte) (value >>> (7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1<<21)) {
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1<<28)) {
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1L<<35)) {
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1L<<42)) {
                writer.putByte((byte) ((value >>> 35) | 0x80));
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1L<<49)) {
                writer.putByte((byte) ((value >>> 42) | 0x80));
                writer.putByte((byte) ((value >>> 35) | 0x80));
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else if (value < (1L<<56)) {
                writer.putByte((byte) ((value >>> 49) | 0x80));
                writer.putByte((byte) ((value >>> 42) | 0x80));
                writer.putByte((byte) ((value >>> 35) | 0x80));
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
            else {
                writer.putByte((byte) ((value >>> 56) | 0x80));
                writer.putByte((byte) ((value >>> 49) | 0x80));
                writer.putByte((byte) ((value >>> 42) | 0x80));
                writer.putByte((byte) ((value >>> 35) | 0x80));
                writer.putByte((byte) ((value >>> 28) | 0x80));
                writer.putByte((byte) ((value >>> 21) | 0x80));
                writer.putByte((byte) ((value >>> 14) | 0x80));
                writer.putByte((byte) ((value >>> 7) | 0x80));
                writer.putByte((byte) (value & 0x7F));
            }
        }

        public void put(long[] values) throws IOException {
            for (long val : values) {
                put(val);
            }
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            writer.close();
        }
    }

    private static class ReaderBE implements VarintColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageReader reader;

        private long position = 0;

        public ReaderBE(ColumnDesc<?,?> columnDesc, StorageReader reader) throws IOException {
            this.columnDesc = columnDesc;
            this.reader = reader;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public int get() throws IOException {
            int value = 0;
            int shift = 0;
            byte b;

            do {
                b = reader.getByte();
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);

            position++;

            return value;
        }

        public long getLong() throws IOException {
            long value = 0;
            int shift = 0;
            byte b;

            do {
                b = reader.getByte();
                value |= (long) (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);

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

    private static class ReaderLE implements VarintColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageReader reader;

        private long position = 0;

        public ReaderLE(ColumnDesc<?,?> columnDesc, StorageReader reader) throws IOException {
            this.columnDesc = columnDesc;
            this.reader = reader;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public int get() throws IOException {
            position++;

            byte b = reader.getByte();
            if ((b & 0x80) == 0) {
                return b;
            }

            int value = b & 0x7F;
            do {
                b = reader.getByte();
                value = (value << 7) | (b & 0x7F);
            } while ((b & 0x80) != 0);


            return value;
        }

        public long getLong() throws IOException {
            position++;

            byte b = reader.getByte();
            if ((b & 0x80) == 0) {
                return b;
            }

            long value = b & 0x7F;
            do {
                b = reader.getByte();
                value = value << 7 | (b & 0x7F);
            } while ((b & 0x80) != 0);

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
