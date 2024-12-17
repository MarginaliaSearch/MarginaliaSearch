package nu.marginalia.sequence.slop;

import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.slop.column.AbstractColumn;
import nu.marginalia.slop.column.AbstractObjectColumn;
import nu.marginalia.slop.column.ObjectColumnReader;
import nu.marginalia.slop.column.ObjectColumnWriter;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Slop column extension for storing GammaCodedSequence objects. */
public class GammaCodedSequenceArrayColumn extends AbstractObjectColumn<List<GammaCodedSequence>, GammaCodedSequenceArrayColumn.Reader, GammaCodedSequenceArrayColumn.Writer> {

    private final VarintColumn groupsColumn;
    private final GammaCodedSequenceColumn dataColumn;

    public GammaCodedSequenceArrayColumn(String name) {
        this(name, StorageType.PLAIN);
    }

    public GammaCodedSequenceArrayColumn(String name, StorageType storageType) {
        super(name,
                "gcs[]",
                ByteOrder.nativeOrder(),
                ColumnFunction.DATA,
                storageType);

        groupsColumn = new VarintColumn(name, ColumnFunction.GROUP_LENGTH, storageType);
        dataColumn = new GammaCodedSequenceColumn(name);
    }

    public Writer createUnregistered(Path path, int page) throws IOException {
        return new Writer(
                dataColumn.createUnregistered(path, page),
                groupsColumn.createUnregistered(path, page)
        );
    }

    @Override
    public int alignmentSize() {
        return 1;
    }

    public Reader openUnregistered(URI uri, int page) throws IOException {
        return new Reader(
                dataColumn.openUnregistered(uri, page),
                groupsColumn.openUnregistered(uri, page)
        );
    }


    public class Writer implements ObjectColumnWriter<List<GammaCodedSequence>> {
        private final VarintColumn.Writer groupsWriter;
        private final GammaCodedSequenceColumn.Writer dataWriter;

        Writer(GammaCodedSequenceColumn.Writer dataWriter, VarintColumn.Writer groupsWriter)
        {
            this.groupsWriter = groupsWriter;
            this.dataWriter = dataWriter;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return GammaCodedSequenceArrayColumn.this;
        }

        @Override
        public void put(List<GammaCodedSequence> sequences) throws IOException {
            groupsWriter.put(sequences.size());
            for (GammaCodedSequence sequence : sequences) {
                dataWriter.put(sequence);
            }
        }

        public long position() {
            return groupsWriter.position();
        }

        public void close() throws IOException {
            dataWriter.close();
            groupsWriter.close();
        }
    }

    public class Reader implements ObjectColumnReader<List<GammaCodedSequence>> {
        private final GammaCodedSequenceColumn.Reader dataReader;
        private final VarintColumn.Reader groupsReader;

        public Reader(GammaCodedSequenceColumn.Reader dataReader, VarintColumn.Reader groupsReader) {
            this.dataReader = dataReader;
            this.groupsReader = groupsReader;
        }

        @Override
        public AbstractColumn<?, ?> columnDesc() {
            return GammaCodedSequenceArrayColumn.this;
        }

        @Override
        public void skip(long positions) throws IOException {
            int toSkip = 0;
            for (int i = 0; i < positions; i++) {
                toSkip += groupsReader.get();
            }
            dataReader.skip(toSkip);
        }

        @Override
        public boolean isDirect() {
            return dataReader.isDirect();
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return groupsReader.hasRemaining();
        }

        public long position() throws IOException {
            return groupsReader.position();
        }

        @Override
        public List<GammaCodedSequence> get() throws IOException {
            int count = groupsReader.get();
            var ret = new ArrayList<GammaCodedSequence>(count);

            for (int i = 0; i < count; i++) {
                ret.add(dataReader.get());
            }

            return ret;
        }

        public List<ByteBuffer> getData(ByteBuffer workArea) throws IOException {
            int count = groupsReader.get();
            var ret = new ArrayList<ByteBuffer>(count);

            for (int i = 0; i < count; i++) {
                int start = workArea.position();
                dataReader.getData(workArea);
                var slice = workArea.slice(start, workArea.position() - start);
                ret.add(slice);
            }

            return ret;
        }


        public void close() throws IOException {
            dataReader.close();
            groupsReader.close();
        }

    }
}
