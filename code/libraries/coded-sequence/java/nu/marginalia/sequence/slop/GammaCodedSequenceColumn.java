package nu.marginalia.sequence.slop;

import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.column.AbstractObjectColumn;
import nu.marginalia.slop.column.ObjectColumnReader;
import nu.marginalia.slop.column.ObjectColumnWriter;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.Storage;
import nu.marginalia.slop.storage.StorageReader;
import nu.marginalia.slop.storage.StorageWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/** Slop column extension for storing GammaCodedSequence objects. */
public class GammaCodedSequenceColumn extends AbstractObjectColumn<GammaCodedSequence, GammaCodedSequenceColumn.Reader, GammaCodedSequenceColumn.Writer> {

    private final VarintColumn indexColumn;

    public GammaCodedSequenceColumn(String name) {
        this(name, StorageType.PLAIN);
    }

    public GammaCodedSequenceColumn(String name, StorageType storageType) {
        super(name,
                "gamma",
                ByteOrder.nativeOrder(),
                ColumnFunction.DATA,
                storageType);

        indexColumn = new VarintColumn(name, ColumnFunction.DATA_LEN, StorageType.PLAIN);
    }

    public Writer createUnregistered(Path path, int page) throws IOException {
        return new Writer(
                Storage.writer(path, this, page),
                indexColumn.createUnregistered(path, page)
        );
    }

    public Reader openUnregistered(Path path, int page) throws IOException {
        return new Reader(
                Storage.reader(path, this, page, false),
                indexColumn.openUnregistered(path, page)
        );
    }

    public class Writer implements ObjectColumnWriter<GammaCodedSequence> {
        private final VarintColumn.Writer indexWriter;
        private final StorageWriter storage;

        public Writer(StorageWriter storage,
                      VarintColumn.Writer indexWriter)
        {
            this.storage = storage;

            this.indexWriter = indexWriter;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return GammaCodedSequenceColumn.this;
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

    public class Reader implements ObjectColumnReader<GammaCodedSequence> {
        private final VarintColumn.Reader indexReader;
        private final StorageReader storage;

        Reader(StorageReader reader, VarintColumn.Reader indexReader) throws IOException {
            this.storage = reader;
            this.indexReader = indexReader;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return GammaCodedSequenceColumn.this;
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
