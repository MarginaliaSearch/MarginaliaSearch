package nu.marginalia.sequence.slop;

import nu.marginalia.sequence.GammaCodedSequence;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/** Slop column extension for storing GammaCodedSequence objects. */
public class GammaCodedSequenceColumn {

    public static ColumnType<GammaCodedSequenceReader, GammaCodedSequenceWriter> TYPE = ColumnType.register("s8[]+gcs", ByteOrder.nativeOrder(), GammaCodedSequenceColumn::open, GammaCodedSequenceColumn::create);

    public static GammaCodedSequenceReader open(Path path, ColumnDesc name) throws IOException {
        return new Reader(
                Storage.reader(path, name, false), // note we must never pass aligned=true here, as the data is not guaranteed alignment
                VarintColumn.open(path, name.createSupplementaryColumn(ColumnFunction.DATA_LEN,
                        ColumnType.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    public static GammaCodedSequenceWriter create(Path path, ColumnDesc name) throws IOException {
        return new Writer(
                Storage.writer(path, name),
                VarintColumn.create(path, name.createSupplementaryColumn(ColumnFunction.DATA_LEN,
                        ColumnType.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    private static class Writer implements GammaCodedSequenceWriter {
        private final VarintColumnWriter indexWriter;
        private final StorageWriter storage;

        public Writer(StorageWriter storage,
                      VarintColumnWriter indexWriter)
        {
            this.storage = storage;

            this.indexWriter = indexWriter;
        }


        @Override
        public void put(GammaCodedSequence sequence) throws IOException {
            var buffer = sequence.buffer();
            int length = buffer.remaining();

            indexWriter.put(length);
            storage.putBytes(buffer);
        }

        public long position() {
            return indexWriter.position();
        }

        public void close() throws IOException {
            indexWriter.close();
            storage.close();
        }
    }

    private static class Reader implements GammaCodedSequenceReader {
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
        public GammaCodedSequence get(ByteBuffer workArea) throws IOException {
            int size = (int) indexReader.get();

            workArea.clear();
            workArea.limit(size);
            storage.getBytes(workArea);
            workArea.flip();

            return new GammaCodedSequence(workArea);
        }

        @Override
        public void getData(ByteBuffer workArea) throws IOException {
            int size = (int) indexReader.get();

            int oldLimit = workArea.limit();
            workArea.limit(workArea.position() + size);
            storage.getBytes(workArea);
            workArea.limit(oldLimit);
        }


        public void close() throws IOException {
            indexReader.close();
            storage.close();
        }

    }
}
