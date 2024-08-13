package nu.marginalia.sequence.slop;

import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.slop.ColumnTypes;
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

    public static ColumnType<GammaCodedSequenceReader, GammaCodedSequenceWriter> TYPE = ColumnTypes.register("s8[]+gcs", ByteOrder.nativeOrder(), GammaCodedSequenceColumn::open, GammaCodedSequenceColumn::create);

    public static GammaCodedSequenceReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(columnDesc,
                Storage.reader(path, columnDesc, false), // note we must never pass aligned=true here, as the data is not guaranteed alignment
                VarintColumn.open(path, columnDesc.createSupplementaryColumn(ColumnFunction.DATA_LEN,
                        ColumnTypes.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    public static GammaCodedSequenceWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(columnDesc,
                Storage.writer(path, columnDesc),
                VarintColumn.create(path, columnDesc.createSupplementaryColumn(ColumnFunction.DATA_LEN,
                        ColumnTypes.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    private static class Writer implements GammaCodedSequenceWriter {
        private final VarintColumnWriter indexWriter;
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageWriter storage;

        public Writer(ColumnDesc<?, ?> columnDesc,
                      StorageWriter storage,
                      VarintColumnWriter indexWriter)
        {
            this.columnDesc = columnDesc;
            this.storage = storage;

            this.indexWriter = indexWriter;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
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
        private final ColumnDesc<?, ?> columnDesc;
        private final StorageReader storage;

        public Reader(ColumnDesc<?, ?> columnDesc, StorageReader reader, VarintColumnReader indexReader) throws IOException {
            this.columnDesc = columnDesc;
            this.storage = reader;
            this.indexReader = indexReader;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        @Override
        public void skip(long positions) throws IOException {
            for (int i = 0; i < positions; i++) {
                int size = indexReader.get();
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
        public GammaCodedSequence get() throws IOException {
            int size = indexReader.get();

            ByteBuffer dest = ByteBuffer.allocate(size);
            storage.getBytes(dest);
            dest.flip();

            return new GammaCodedSequence(dest);
        }

        @Override
        public void getData(ByteBuffer workArea) throws IOException {
            int size = indexReader.get();

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
