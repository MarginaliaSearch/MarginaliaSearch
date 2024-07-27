package nu.marginalia.slop.column.dynamic;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.file.Path;

public class CustomBinaryColumn {

    public static CustomBinaryColumnReader open(Path path, ColumnDesc name) throws IOException {
        return new Reader(
                Storage.reader(path, name, false), // note we must never pass aligned=true here, as the data is not guaranteed alignment
                VarintColumn.open(path, name.createSupplementaryColumn(ColumnFunction.DATA_LEN,
                        ColumnType.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    public static CustomBinaryColumnWriter create(Path path, ColumnDesc name) throws IOException {
        return new Writer(
                Storage.writer(path, name),
                VarintColumn.create(path, name.createSupplementaryColumn(ColumnFunction.DATA_LEN,
                        ColumnType.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    private static class Writer implements CustomBinaryColumnWriter {
        private final VarintColumnWriter indexWriter;
        private final StorageWriter storage;

        public Writer(StorageWriter storage,
                      VarintColumnWriter indexWriter)
        {
            this.storage = storage;

            this.indexWriter = indexWriter;
        }

        @Override
        public RecordWriter next() throws IOException {
            return new RecordWriter() {
                long pos = storage.position();

                @Override
                public StorageWriter writer() {
                    return storage;
                }

                @Override
                public void close() throws IOException {
                    indexWriter.put((int) (storage.position() - pos));
                }
            };
        }

        public long position() {
            return indexWriter.position();
        }

        public void close() throws IOException {
            indexWriter.close();
            storage.close();
        }
    }

    private static class Reader implements CustomBinaryColumnReader {
        private final VarintColumnReader indexReader;
        private final StorageReader storage;

        public Reader(StorageReader reader, VarintColumnReader indexReader) throws IOException {
            this.storage = reader;
            this.indexReader = indexReader;
        }

        @Override
        public void skip(long positions) throws IOException {
            for (int i = 0; i < positions; i++) {
                int size = (int) indexReader.get();
                storage.skip(size, 1);
            }
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return indexReader.hasRemaining();
        }

        public long position() throws IOException {
            return indexReader.position();
        }

        @Override
        public RecordReader next() throws IOException {
            int size = (int) indexReader.get();

            return new RecordReader() {
                long origPos = storage.position();

                @Override
                public int size() {
                    return size;
                }

                @Override
                public StorageReader reader() {
                    return storage;
                }

                @Override
                public void close() throws IOException {
                    assert storage.position() - origPos == size : "column reader caller did not read the entire record";
                }
            };
        }

        public void close() throws IOException {
            indexReader.close();
            storage.close();
        }

    }
}
