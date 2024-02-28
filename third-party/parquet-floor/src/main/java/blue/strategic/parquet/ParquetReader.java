package blue.strategic.parquet;

import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.ColumnReadStore;
import org.apache.parquet.column.ColumnReader;
import org.apache.parquet.column.impl.ColumnReadStoreImpl;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.DummyRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.FileMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.io.DelegatingSeekableInputStream;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;
import org.apache.parquet.io.api.GroupConverter;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ParquetReader<U, S> implements Spliterator<S>, Closeable {
    private final ParquetFileReader reader;
    private final Hydrator<U, S> hydrator;
    private final List<ColumnDescriptor> columns;
    private final MessageType schema;
    private final GroupConverter recordConverter;
    private final String createdBy;

    private boolean finished;
    private long currentRowGroupSize = -1L;
    private List<ColumnReader> currentRowGroupColumnReaders;
    private long currentRowIndex = -1L;

    public static <U, S> Stream<S> streamContent(File file, HydratorSupplier<U, S> hydrator) throws IOException {
        return streamContent(file, hydrator, null);
    }

    public static <U, S> Stream<S> streamContent(File file, HydratorSupplier<U, S> hydrator, Collection<String> columns) throws IOException {
        return streamContent(makeInputFile(file), hydrator, columns);
    }

    public static <U, S> Stream<S> streamContent(InputFile file, HydratorSupplier<U, S> hydrator) throws IOException {
        return streamContent(file, hydrator, null);
    }

    public static <U, S> Stream<S> streamContent(InputFile file, HydratorSupplier<U, S> hydrator, Collection<String> columns) throws IOException {
        return stream(spliterator(file, hydrator, columns));
    }

    public static <U, S> ParquetReader<U, S> spliterator(File file, HydratorSupplier<U, S> hydrator) throws IOException {
        return spliterator(file, hydrator, null);
    }

    public static <U, S> ParquetReader<U, S> spliterator(File file, HydratorSupplier<U, S> hydrator, Collection<String> columns) throws IOException {
        return spliterator(makeInputFile(file), hydrator, columns);
    }

    public static <U, S> ParquetReader<U, S> spliterator(InputFile file, HydratorSupplier<U, S> hydrator) throws IOException {
        return spliterator(file, hydrator, null);
    }

    public static <U, S> ParquetReader<U, S> spliterator(InputFile file, HydratorSupplier<U, S> hydrator, Collection<String> columns) throws IOException {
        Set<String> columnSet = (null == columns) ? Collections.emptySet() : Set.copyOf(columns);
        return new ParquetReader<>(file, columnSet, hydrator);
    }

    public static <U, S> Stream<S> stream(ParquetReader<U, S> reader) {
        return StreamSupport
                .stream(reader, false)
                .onClose(() -> closeSilently(reader));
    }

    public static Stream<String[]> streamContentToStrings(File file) throws IOException {
        return stream(spliterator(makeInputFile(file), columns -> {
            final AtomicInteger pos = new AtomicInteger(0);
            return new Hydrator<String[], String[]>() {
                @Override
                public String[] start() {
                    return new String[columns.size()];
                }

                @Override
                public String[] add(String[] target, String heading, Object value) {
                    target[pos.getAndIncrement()] = heading + "=" + value.toString();
                    return target;
                }

                @Override
                public String[] finish(String[] target) {
                    return target;
                }
            };
        }, null));
    }

    public static ParquetMetadata readMetadata(File file) throws IOException {
        return readMetadata(makeInputFile(file));
    }

    public static ParquetMetadata readMetadata(InputFile file) throws IOException {
        try (ParquetFileReader reader = ParquetFileReader.open(file)) {
            return reader.getFooter();
        }
    }

    private ParquetReader(InputFile file, Set<String> columnNames, HydratorSupplier<U, S> hydratorSupplier) throws IOException {
        this.reader = ParquetFileReader.open(file);
        FileMetaData meta = reader.getFooter().getFileMetaData();
        this.schema = meta.getSchema();
        this.recordConverter = new DummyRecordConverter(this.schema).getRootConverter();
        this.createdBy = meta.getCreatedBy();

        this.columns = schema.getColumns().stream()
                .filter(c -> columnNames.isEmpty() || columnNames.contains(c.getPath()[0]))
                .collect(Collectors.toList());

        this.hydrator = hydratorSupplier.get(this.columns);
    }

    private static void closeSilently(Closeable resource) {
        try {
            resource.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private static Object readValue(ColumnReader columnReader) {
        ColumnDescriptor column = columnReader.getDescriptor();
        PrimitiveType primitiveType = column.getPrimitiveType();
        int maxDefinitionLevel = column.getMaxDefinitionLevel();

        if (columnReader.getCurrentDefinitionLevel() == maxDefinitionLevel) {
            switch (primitiveType.getPrimitiveTypeName()) {
            case BINARY:
            case FIXED_LEN_BYTE_ARRAY:
            case INT96:
                if (primitiveType.getLogicalTypeAnnotation() == null) {
                    return columnReader.getBinary().getBytes();
                } else {
                    return primitiveType.stringifier().stringify(columnReader.getBinary());
                }
            case BOOLEAN:
                return columnReader.getBoolean();
            case DOUBLE:
                return columnReader.getDouble();
            case FLOAT:
                return columnReader.getFloat();
            case INT32:
                return columnReader.getInteger();
            case INT64:
                return columnReader.getLong();
            default:
                throw new IllegalArgumentException("Unsupported type: " + primitiveType);
            }
        } else {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public boolean tryAdvance(Consumer<? super S> action) {
        try {
            if (this.finished) {
                return false;
            }

            if (currentRowIndex == currentRowGroupSize) {
                PageReadStore rowGroup = reader.readNextRowGroup();
                if (rowGroup == null) {
                    this.finished = true;
                    return false;
                }

                ColumnReadStore columnReadStore = new ColumnReadStoreImpl(rowGroup, this.recordConverter, this.schema, this.createdBy);

                this.currentRowGroupSize = rowGroup.getRowCount();
                this.currentRowGroupColumnReaders = columns.stream().map(columnReadStore::getColumnReader).collect(Collectors.toList());
                this.currentRowIndex = 0L;
            }

            U record = hydrator.start();
            for (ColumnReader columnReader: this.currentRowGroupColumnReaders) {
                do {
                    var value = readValue(columnReader);
                    if (value != null) {
                        record = hydrator.add(record, columnReader.getDescriptor().getPath()[0], value);
                    }
                    columnReader.consume();
                } while (columnReader.getCurrentRepetitionLevel() != 0);

                if (columnReader.getCurrentRepetitionLevel() != 0) {
                    throw new IllegalStateException("Unexpected repetition");
                }
            }

            action.accept(hydrator.finish(record));
            this.currentRowIndex++;

            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read parquet", e);
        }
    }

    @Override
    public Spliterator<S> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return reader.getRecordCount();
    }

    @Override
    public int characteristics() {
        return ORDERED | NONNULL | DISTINCT;
    }

    public ParquetMetadata metaData() {
        return this.reader.getFooter();
    }

    public static InputFile makeInputFile(File file) {
        return new InputFile() {
            @Override
            public long getLength() {
                return file.length();
            }

            @Override
            public SeekableInputStream newStream() throws IOException {
                FileInputStream fis = new FileInputStream(file);
                return new DelegatingSeekableInputStream(fis) {
                    private long position;

                    @Override
                    public long getPos() {
                        return position;
                    }

                    @Override
                    public void seek(long newPos) throws IOException {
                        fis.getChannel().position(newPos);
                        position = newPos;
                    }
                };
            }
        };
    }
}
