package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.array.ByteArrayColumnReader;
import nu.marginalia.slop.column.array.ByteArrayColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class StringColumn {

    public static StringColumnReader open(Path path, ColumnDesc name) throws IOException {
        if (name.type().equals(ColumnType.STRING)) {
            return new ArrayReader(ByteArrayColumn.open(path, name));
        } else if (name.type().equals(ColumnType.CSTRING)) {
            return new CStringReader(Storage.reader(path, name, true));
        } else if (name.type().equals(ColumnType.TXTSTRING)) {
            return new TxtStringReader(Storage.reader(path, name, true));
        }
        throw new IllegalArgumentException("Unsupported column type: " + name.type());
    }

    public static StringColumnWriter create(Path path, ColumnDesc name) throws IOException {
        if (name.type().equals(ColumnType.STRING)) {
            return new ArrayWriter(ByteArrayColumn.create(path, name));
        } else if (name.type().equals(ColumnType.CSTRING)) {
            return new CStringWriter(Storage.writer(path, name));
        } else if (name.type().equals(ColumnType.TXTSTRING)) {
            return new TxtStringWriter(Storage.writer(path, name));
        }
        throw new IllegalArgumentException("Unsupported column type: " + name.type());
    }

    private static class ArrayWriter implements StringColumnWriter {
        private final ByteArrayColumnWriter backingColumn;

        public ArrayWriter(ByteArrayColumnWriter backingColumn) throws IOException {
            this.backingColumn = backingColumn;
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
        private final ByteArrayColumnReader backingColumn;

        public ArrayReader(ByteArrayColumnReader backingColumn) throws IOException {
            this.backingColumn = backingColumn;
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
            backingColumn.seek(positions);
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
        private final StorageWriter storageWriter;

        private long position = 0;

        public CStringWriter(StorageWriter storageWriter) throws IOException {
            this.storageWriter = storageWriter;
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
        private final StorageReader storageReader;
        private long position = 0;

        public CStringReader(StorageReader storageReader) throws IOException {
            this.storageReader = storageReader;
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
        private final StorageWriter storageWriter;
        private long position = 0;

        public TxtStringWriter(StorageWriter storageWriter) throws IOException {
            this.storageWriter = storageWriter;
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
        private final StorageReader storageReader;
        private long position = 0;

        public TxtStringReader(StorageReader storageReader) throws IOException {
            this.storageReader = storageReader;
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
