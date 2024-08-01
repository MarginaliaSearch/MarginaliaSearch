package nu.marginalia.slop.column.array;

import nu.marginalia.slop.column.ObjectColumnReader;
import nu.marginalia.slop.column.ObjectColumnWriter;
import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.column.dynamic.VarintColumnReader;
import nu.marginalia.slop.column.dynamic.VarintColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ObjectArrayColumn {
    public static <T> ObjectArrayColumnReader<T> open(Path baseDir,
                                               ColumnDesc<ObjectArrayColumnReader<T>, ObjectArrayColumnWriter<T>> selfType,
                                               ObjectColumnReader<T> baseReader) throws IOException {
        return new Reader<>(selfType, baseReader,
                VarintColumn.open(baseDir, selfType.createSupplementaryColumn(ColumnFunction.GROUP_LENGTH, ColumnType.VARINT_LE, StorageType.PLAIN)));
    }

    public static <T> ObjectArrayColumnWriter<T> create(Path baseDir,
                                                 ColumnDesc<ObjectArrayColumnReader<T>, ObjectArrayColumnWriter<T>> selfType,
                                                 ObjectColumnWriter<T> baseWriter) throws IOException {
        return new Writer<T>(selfType,
                baseWriter,
                VarintColumn.create(baseDir, selfType.createSupplementaryColumn(ColumnFunction.GROUP_LENGTH, ColumnType.VARINT_LE, StorageType.PLAIN)));
    }


    private static class Writer<T> implements ObjectArrayColumnWriter<T> {
        private final ColumnDesc<?, ?> columnDesc;
        private final ObjectColumnWriter<T> dataWriter;
        private final VarintColumnWriter groupsWriter;

        public Writer(ColumnDesc<?, ?> columnDesc, ObjectColumnWriter<T> dataWriter, VarintColumnWriter groupsWriter) throws IOException {
            this.columnDesc = columnDesc;
            this.dataWriter = dataWriter;
            this.groupsWriter = groupsWriter;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(List<T> value) throws IOException {
            groupsWriter.put(value.size());
            for (T t : value) {
                dataWriter.put(t);
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

    private static class Reader<T> implements ObjectArrayColumnReader<T> {
        private final ColumnDesc<?, ?> columnDesc;
        private final ObjectColumnReader<T> dataReader;
        private final VarintColumnReader groupsReader;

        public Reader(ColumnDesc<?, ?> columnDesc, ObjectColumnReader<T> dataReader, VarintColumnReader groupsReader) throws IOException {
            this.columnDesc = columnDesc;
            this.dataReader = dataReader;
            this.groupsReader = groupsReader;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public List<T> get() throws IOException {
            int length = groupsReader.get();
            List<T> ret = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                ret.add(dataReader.get());
            }
            return ret;
        }

        @Override
        public long position() throws IOException {
            return groupsReader.position();
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

        @Override
        public void close() throws IOException {
            dataReader.close();
            groupsReader.close();
        }
    }
}
