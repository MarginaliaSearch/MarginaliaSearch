package nu.marginalia.slop.column.string;

import nu.marginalia.slop.column.dynamic.VarintColumn;
import nu.marginalia.slop.column.primitive.LongColumnReader;
import nu.marginalia.slop.column.primitive.LongColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EnumColumn {

    public static StringColumnReader open(Path path, ColumnDesc name) throws IOException {
        return new Reader(
                StringColumn.open(path,
                        name.createSupplementaryColumn(
                                ColumnFunction.DICT,
                                ColumnType.TXTSTRING,
                                StorageType.PLAIN)
                ),
                VarintColumn.open(path,
                        name.createSupplementaryColumn(
                                ColumnFunction.DATA,
                                ColumnType.ENUM_LE,
                                StorageType.PLAIN
                        )
                )
        );
    }

    public static StringColumnWriter create(Path path, ColumnDesc name) throws IOException {
        return new Writer(
                StringColumn.create(path, name.createSupplementaryColumn(ColumnFunction.DICT, ColumnType.TXTSTRING, StorageType.PLAIN)),
                VarintColumn.create(path, name.createSupplementaryColumn(ColumnFunction.DATA, ColumnType.ENUM_LE, StorageType.PLAIN))
        );
    }


    private static class Writer implements StringColumnWriter {
        private final StringColumnWriter dicionaryColumn;
        private final LongColumnWriter dataColumn;
        private final HashMap<String, Integer> dictionary = new HashMap<>();

        public Writer(StringColumnWriter dicionaryColumn,
                      LongColumnWriter dataColumn) throws IOException
        {
            this.dicionaryColumn = dicionaryColumn;
            this.dataColumn = dataColumn;
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

    private static class Reader implements StringColumnReader {
        private final LongColumnReader dataColumn;
        private final List<String> dictionary = new ArrayList<>();

        public Reader(StringColumnReader dicionaryColumn,
                      LongColumnReader dataColumn) throws IOException
        {
            this.dataColumn = dataColumn;
            for (int i = 0; dicionaryColumn.hasRemaining(); i++) {
                dictionary.add(dicionaryColumn.get());
            }
            dicionaryColumn.close();
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

}
