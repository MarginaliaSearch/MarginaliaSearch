package nu.marginalia.sequence.slop;

import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.column.dynamic.VarintColumnReader;
import nu.marginalia.slop.column.dynamic.VarintColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Slop column extension for storing GammaCodedSequence objects. */
public class GammaCodedSequenceArrayColumn {

    public static ColumnType<GammaCodedSequenceArrayReader, GammaCodedSequenceArrayWriter> TYPE = ColumnType.register("s8[]+gcs[]", ByteOrder.nativeOrder(), GammaCodedSequenceArrayColumn::open, GammaCodedSequenceArrayColumn::create);

    public static GammaCodedSequenceArrayReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(columnDesc,
                GammaCodedSequenceColumn.open(path, columnDesc),
                VarintColumn.open(path, columnDesc.createSupplementaryColumn(ColumnFunction.GROUP_LENGTH,
                        ColumnType.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    public static GammaCodedSequenceArrayWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(columnDesc,
                GammaCodedSequenceColumn.create(path, columnDesc),
                VarintColumn.create(path, columnDesc.createSupplementaryColumn(ColumnFunction.GROUP_LENGTH,
                        ColumnType.VARINT_LE,
                        StorageType.PLAIN)
                )
        );
    }

    private static class Writer implements GammaCodedSequenceArrayWriter {
        private final VarintColumnWriter groupsWriter;
        private final GammaCodedSequenceWriter dataWriter;
        private final ColumnDesc<?, ?> columnDesc;

        public Writer(ColumnDesc<?, ?> columnDesc, GammaCodedSequenceWriter dataWriter, VarintColumnWriter groupsWriter)
        {
            this.groupsWriter = groupsWriter;
            this.dataWriter = dataWriter;
            this.columnDesc = columnDesc;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
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

    private static class Reader implements GammaCodedSequenceArrayReader {
        private final GammaCodedSequenceReader dataReader;
        private final VarintColumnReader groupsReader;
        private final ColumnDesc<?, ?> columnDesc;

        public Reader(ColumnDesc<?, ?> columnDesc, GammaCodedSequenceReader dataReader, VarintColumnReader groupsReader) throws IOException {
            this.dataReader = dataReader;
            this.groupsReader = groupsReader;
            this.columnDesc = columnDesc;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
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

        @Override
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
