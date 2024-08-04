package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.column.dynamic.VarintColumnReader;
import nu.marginalia.slop.column.primitive.ByteColumn;
import nu.marginalia.slop.column.primitive.ByteColumnReader;
import nu.marginalia.slop.column.primitive.ByteColumnWriter;
import nu.marginalia.slop.column.primitive.LongColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class EnumColumn {

    public static EnumColumnReader open(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader(
                columnDesc,
                StringColumn.open(path,
                        columnDesc.createSupplementaryColumn(
                                ColumnFunction.DICT,
                                ColumnType.TXTSTRING,
                                StorageType.PLAIN)
                ),
                VarintColumn.open(path,
                        columnDesc.createSupplementaryColumn(
                                ColumnFunction.DATA,
                                ColumnType.ENUM_LE,
                                columnDesc.storageType()
                        )
                )
        );
    }
    public static EnumColumnReader open8(Path path, ColumnDesc columnDesc) throws IOException {
        return new Reader8(
                columnDesc,
                StringColumn.open(path,
                        columnDesc.createSupplementaryColumn(
                                ColumnFunction.DICT,
                                ColumnType.TXTSTRING,
                                StorageType.PLAIN)
                ),
                ByteColumn.open(path,
                        columnDesc.createSupplementaryColumn(
                                ColumnFunction.DATA,
                                ColumnType.BYTE,
                                columnDesc.storageType()
                        )
                )
        );
    }

    public static StringColumnWriter create(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer(columnDesc,
                StringColumn.create(path, columnDesc.createSupplementaryColumn(ColumnFunction.DICT, ColumnType.TXTSTRING, StorageType.PLAIN)),
                VarintColumn.create(path, columnDesc.createSupplementaryColumn(ColumnFunction.DATA, ColumnType.ENUM_LE, columnDesc.storageType()))
        );
    }

    public static StringColumnWriter create8(Path path, ColumnDesc columnDesc) throws IOException {
        return new Writer8(columnDesc,
                StringColumn.create(path, columnDesc.createSupplementaryColumn(ColumnFunction.DICT, ColumnType.TXTSTRING, StorageType.PLAIN)),
                ByteColumn.create(path, columnDesc.createSupplementaryColumn(ColumnFunction.DATA, ColumnType.BYTE, columnDesc.storageType()))
        );
    }

    private static class Writer implements StringColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final StringColumnWriter dicionaryColumn;
        private final LongColumnWriter dataColumn;
        private final HashMap<String, Integer> dictionary = new HashMap<>();

        public Writer(ColumnDesc<?, ?> columnDesc,
                      StringColumnWriter dicionaryColumn,
                      LongColumnWriter dataColumn) throws IOException
        {
            this.columnDesc = columnDesc;
            this.dicionaryColumn = dicionaryColumn;
            this.dataColumn = dataColumn;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(String value) throws IOException {
            Integer index = dictionary.get(value);
            if (index == null) {
                index = dictionary.size();
                dictionary.put(value, index);
                dicionaryColumn.put(value);
            }
            dataColumn.put(index);
        }

        public long position() {
            return dataColumn.position();
        }

        public void close() throws IOException {
            dataColumn.close();
            dicionaryColumn.close();
        }
    }

    private static class Writer8 implements StringColumnWriter {
        private final ColumnDesc<?, ?> columnDesc;
        private final StringColumnWriter dicionaryColumn;
        private final ByteColumnWriter dataColumn;
        private final HashMap<String, Integer> dictionary = new HashMap<>();

        public Writer8(ColumnDesc<?, ?> columnDesc,
                      StringColumnWriter dicionaryColumn,
                      ByteColumnWriter dataColumn) throws IOException
        {
            this.columnDesc = columnDesc;
            this.dicionaryColumn = dicionaryColumn;
            this.dataColumn = dataColumn;
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        public void put(String value) throws IOException {
            Integer index = dictionary.get(value);
            if (index == null) {
                index = dictionary.size();
                dictionary.put(value, index);
                dicionaryColumn.put(value);
            }
            dataColumn.put((byte) index.intValue());
        }

        public long position() {
            return dataColumn.position();
        }

        public void close() throws IOException {
            dataColumn.close();
            dicionaryColumn.close();
        }
    }

    private static class Reader implements EnumColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final VarintColumnReader dataColumn;
        private final List<String> dictionary = new ArrayList<>();

        public Reader(ColumnDesc<?, ?> columnDesc,
                      StringColumnReader dicionaryColumn,
                      VarintColumnReader dataColumn) throws IOException
        {
            this.columnDesc = columnDesc;
            this.dataColumn = dataColumn;

            while (dicionaryColumn.hasRemaining()) {
                dictionary.add(dicionaryColumn.get());
            }

            dicionaryColumn.close();
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        @Override
        public List<String> getDictionary() throws IOException {
            return Collections.unmodifiableList(dictionary);
        }

        @Override
        public int getOrdinal() throws IOException {
            return (int) dataColumn.get();
        }

        public String get() throws IOException {
            int index = (int) dataColumn.get();
            return dictionary.get(index);
        }

        @Override
        public long position() throws IOException {
            return dataColumn.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            dataColumn.skip(positions);
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return dataColumn.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            dataColumn.close();
        }
    }

    private static class Reader8 implements EnumColumnReader {
        private final ColumnDesc<?, ?> columnDesc;
        private final ByteColumnReader dataColumn;
        private final List<String> dictionary = new ArrayList<>();

        public Reader8(ColumnDesc<?, ?> columnDesc,
                      StringColumnReader dicionaryColumn,
                      ByteColumnReader dataColumn) throws IOException
        {
            this.columnDesc = columnDesc;
            this.dataColumn = dataColumn;

            while (dicionaryColumn.hasRemaining()) {
                dictionary.add(dicionaryColumn.get());
            }

            dicionaryColumn.close();
        }

        @Override
        public ColumnDesc<?, ?> columnDesc() {
            return columnDesc;
        }

        @Override
        public List<String> getDictionary() throws IOException {
            return Collections.unmodifiableList(dictionary);
        }

        @Override
        public int getOrdinal() throws IOException {
            return  dataColumn.get();
        }

        public String get() throws IOException {
            int index = dataColumn.get();
            return dictionary.get(index);
        }

        @Override
        public long position() throws IOException {
            return dataColumn.position();
        }

        @Override
        public void skip(long positions) throws IOException {
            dataColumn.skip(positions);
        }

        @Override
        public boolean hasRemaining() throws IOException {
            return dataColumn.hasRemaining();
        }

        @Override
        public void close() throws IOException {
            dataColumn.close();
        }
    }
}
