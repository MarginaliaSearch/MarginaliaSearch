package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.array.*;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class StringColumn {

    public static StringColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        if (columnDesc.type().equals(ColumnType.STRING)) {
            return new ArrayReader(columnDesc, ByteArrayColumn.open(path, columnDesc));
        } else if (columnDesc.type().equals(ColumnType.CSTRING)) {
            return new CStringReader(columnDesc, Storage.reader(path, columnDesc, true));
        } else if (columnDesc.type().equals(ColumnType.TXTSTRING)) {
            return new TxtStringReader(columnDesc, Storage.reader(path, columnDesc, true));
        }
        throw new IllegalArgumentException("Unsupported column type: " + columnDesc.type());
    }


    public static StringColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        if (columnDesc.type().equals(ColumnType.STRING)) {
            return new ArrayWriter(columnDesc, ByteArrayColumn.create(path, columnDesc));
        } else if (columnDesc.type().equals(ColumnType.CSTRING)) {
            return new CStringWriter(columnDesc, Storage.writer(path, columnDesc));
        } else if (columnDesc.type().equals(ColumnType.TXTSTRING)) {
            return new TxtStringWriter(columnDesc, Storage.writer(path, columnDesc));
        }
        throw new IllegalArgumentException("Unsupported column type: " + columnDesc.type());
    }

    public static ObjectArrayColumnReader<String> openArray(Path path, ColumnDesc columnDesc) throws IOException {
        if (columnDesc.type().equals(ColumnType.STRING_ARRAY)) {
            return ObjectArrayColumn.open(path, columnDesc, new ArrayReader(columnDesc, ByteArrayColumn.open(path, columnDesc)));
        } else if (columnDesc.type().equals(ColumnType.CSTRING_ARRAY)) {
            return ObjectArrayColumn.open(path, columnDesc, new CStringReader(columnDesc, Storage.reader(path, columnDesc, true)));
        } else if (columnDesc.type().equals(ColumnType.TXTSTRING_ARRAY)) {
            return ObjectArrayColumn.open(path, columnDesc, new TxtStringReader(columnDesc, Storage.reader(path, columnDesc, true)));
        }
        throw new IllegalArgumentException("Unsupported column type: " + columnDesc.type());
    }

    public static ObjectArrayColumnWriter<String> createArray(Path path, ColumnDesc columnDesc) throws IOException {
        if (columnDesc.type().equals(ColumnType.STRING_ARRAY)) {
            return ObjectArrayColumn.create(path, columnDesc, new ArrayWriter(columnDesc, ByteArrayColumn.create(path, columnDesc)));
        } else if (columnDesc.type().equals(ColumnType.CSTRING_ARRAY)) {
            return ObjectArrayColumn.create(path, columnDesc, new CStringWriter(columnDesc, Storage.writer(path, columnDesc)));
        } else if (columnDesc.type().equals(ColumnType.TXTSTRING_ARRAY)) {
            return ObjectArrayColumn.create(path, columnDesc, new TxtStringWriter(columnDesc, Storage.writer(path, columnDesc)));
        }
        throw new IllegalArgumentException("Unsupported column type: " + columnDesc.type());
    }

    private static class ArrayWriter implements StringColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final ByteArrayColumnWriter backingColumn;

        public ArrayWriter(ColumnDesc<?, ?> columnDesc, ByteArrayColumnWriter backingColumn) throws IOException {
            this.columnDesc = columnDesc;
            this.backingColumn = backingColumn;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(String value) throws IOException {
            if (null == value) {
                value = "";
            }

            backingColumn.put(value.getBytes());
        }

        public long position() {
            return backingColumn.position();
        }

        public void close() throws IOException {
            backingColumn.close();
        }
    }

    private static class ArrayReader implements StringColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final ByteArrayColumnReader backingColumn;

        public ArrayReader(ColumnDesc<?, ?> columnDesc, ByteArrayColumnReader backingColumn) throws IOException {
            this.columnDesc = columnDesc;
            this.backingColumn = backingColumn;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public String get() throws IOException {
            return new String(backingColumn.get());
        }

        @Override
        public long position() throws IOException {
            return backingColumn.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            backingColumn.skip(positions);
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return backingColumn.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            backingColumn.close();
        }
    }


    private static class CStringWriter implements StringColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageWriter storageWriter;

        private long position = 0;

        public CStringWriter(ColumnDesc<?,?> columnDesc, StorageWriter storageWriter) throws IOException {
            this.columnDesc = columnDesc;
            this.storageWriter = storageWriter;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(String value) throws IOException {
            if (null == value) {
                value = "";
            }
            assert value.indexOf('\0') == -1 : "Null byte not allowed in cstring";
            storageWriter.putBytes(value.getBytes());
            storageWriter.putByte((byte) 0);
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storageWriter.close();
        }
    }

    private static class CStringReader implements StringColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageReader storageReader;
        private long position = 0;

        public CStringReader(ColumnDesc<?, ?> columnDesc, StorageReader storageReader) throws IOException {
            this.columnDesc = columnDesc;
            this.storageReader = storageReader;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public String get() throws IOException {
            StringBuilder sb = new StringBuilder();
            byte b;
            while (storageReader.hasRemaining() && (b = storageReader.getByte()) != 0) {
                sb.append((char) b);
            }
            position++;
            return sb.toString();
        }

        @Override
        public long position() throws IOException {
            return position;
        }

        @Override
        public void skip(long positions) throws IOException {
            int i = 0;

            while (i < positions && storageReader.hasRemaining()) {
                if (storageReader.getByte() == 0) {
                    i++;
                }
            }
            position += positions;
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return storageReader.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            storageReader.close();
        }
    }


    private static class TxtStringWriter implements StringColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageWriter storageWriter;
        private long position = 0;

        public TxtStringWriter(ColumnDesc<?, ?> columnDesc, StorageWriter storageWriter) throws IOException {
            this.columnDesc = columnDesc;
            this.storageWriter = storageWriter;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(String value) throws IOException {
            if (null == value) {
                value = "";
            }

            assert value.indexOf('\n') == -1 : "Newline not allowed in txtstring";

            storageWriter.putBytes(value.getBytes());
            storageWriter.putByte((byte) '\n');
            position++;
        }

        public long position() {
            return position;
        }

        public void close() throws IOException {
            storageWriter.close();
        }
    }

    private static class TxtStringReader implements StringColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageReader storageReader;
        private long position = 0;

        public TxtStringReader(ColumnDesc<?, ?> columnDesc, StorageReader storageReader) throws IOException {
            this.columnDesc = columnDesc;
            this.storageReader = storageReader;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public String get() throws IOException {
            StringBuilder sb = new StringBuilder();
            byte b;
            while (storageReader.hasRemaining()) {
                b = storageReader.getByte();
                if (b == '\n') {
                    break;
                }
                else {
                    sb.append((char) b);
                }
            }
            position++;
            return sb.toString();
        }

        @Override
        public long position() throws IOException {
            return position;
        }

        @Override
        public void skip(long positions) throws IOException {
            int i = 0;

            position+=positions;

            while (i < positions && storageReader.hasRemaining()) {
                if (storageReader.getByte() == '\n') {
                    i++;
                }
            }
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return storageReader.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            storageReader.close();
        }
    }
}
