package blue.strategic.parquet;

import gnu.trove.list.TIntList;
import gnu.trove.list.TLongList;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.DelegatingPositionOutputStream;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.io.api.RecordConsumer;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64;

public final class ParquetWriter<T> implements Closeable {

    private final org.apache.parquet.hadoop.ParquetWriter<T> writer;

    public static <T> ParquetWriter<T> writeFile(MessageType schema, File out, Dehydrator<T> dehydrator) throws IOException {
        OutputFile f = new OutputFile() {
            @Override
            public PositionOutputStream create(long blockSizeHint) throws IOException {
                return createOrOverwrite(blockSizeHint);
            }

            @Override
            public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
                FileOutputStream fos = new FileOutputStream(out);
                return new DelegatingPositionOutputStream(fos) {
                    @Override
                    public long getPos() throws IOException {
                        return fos.getChannel().position();
                    }
                };
            }

            @Override
            public boolean supportsBlockSize() {
                return false;
            }

            @Override
            public long defaultBlockSize() {
                return 1024L;
            }
        };
        return writeOutputFile(schema, f, dehydrator);
    }

    private static <T> ParquetWriter<T> writeOutputFile(MessageType schema, OutputFile file, Dehydrator<T> dehydrator) throws IOException {
        return new ParquetWriter<>(file, schema, dehydrator);
    }

    private ParquetWriter(OutputFile outputFile, MessageType schema, Dehydrator<T> dehydrator) throws IOException {
        this.writer = new Builder<T>(outputFile)
                .withType(schema)
                .withDehydrator(dehydrator)
                .withCompressionCodec(CompressionCodecName.ZSTD)
                .withWriterVersion(ParquetProperties.WriterVersion.PARQUET_2_0)
                .build();
    }

    public void write(T record) throws IOException {
        writer.write(record);
    }

    @Override
    public void close() throws IOException {
        this.writer.close();
    }

    private static final class Builder<T> extends org.apache.parquet.hadoop.ParquetWriter.Builder<T, ParquetWriter.Builder<T>> {
        private MessageType schema;
        private Dehydrator<T> dehydrator;

        private Builder(OutputFile file) {
            super(file);
        }

        public ParquetWriter.Builder<T> withType(MessageType schema) {
            this.schema = schema;
            return this;
        }

        public ParquetWriter.Builder<T> withDehydrator(Dehydrator<T> dehydrator) {
            this.dehydrator = dehydrator;
            return this;
        }

        @Override
        protected ParquetWriter.Builder<T> self() {
            return this;
        }

        @Override
        protected WriteSupport<T> getWriteSupport(Configuration conf) {
            return new SimpleWriteSupport<>(schema, dehydrator);
        }
    }

    private static class SimpleWriteSupport<T> extends WriteSupport<T> {
        private final MessageType schema;
        private final Dehydrator<T> dehydrator;
        private final ValueWriter valueWriter = new ValueWriter() {
            @Override
            public void write(String name, Object value) {
                SimpleWriteSupport.this.writeField(name, value);
            }

            @Override
            public void writeList(String name, List<?> value) {
                if (value.isEmpty()) {
                    return;
                }

                SimpleWriteSupport.this.writeList(name, value);
            }

            @Override
            public void writeList(String name, TLongList value) {
                if (value.isEmpty()) {
                    return;
                }

                SimpleWriteSupport.this.writeList(name, value);
            }

            @Override
            public void writeList(String name, TIntList value) {
                if (value.isEmpty()) {
                    return;
                }

                SimpleWriteSupport.this.writeList(name, value);
            }
        };

        private RecordConsumer recordConsumer;

        SimpleWriteSupport(MessageType schema, Dehydrator<T> dehydrator) {
            this.schema = schema;
            this.dehydrator = dehydrator;
        }

        @Override
        public WriteContext init(Configuration configuration) {
            return new WriteContext(schema, Collections.emptyMap());
        }

        @Override
        public void prepareForWrite(RecordConsumer recordConsumer) {
            this.recordConsumer = recordConsumer;
        }

        @Override
        public void write(T record) {
            recordConsumer.startMessage();
            dehydrator.dehydrate(record, valueWriter);
            recordConsumer.endMessage();
        }

        @Override
        public String getName() {
            return "blue.strategic.parquet.ParquetWriter";
        }

        private void writeField(String name, Object value) {
            int fieldIndex = schema.getFieldIndex(name);
            PrimitiveType type = schema.getType(fieldIndex).asPrimitiveType();
            recordConsumer.startField(name, fieldIndex);

            writeValue(type, value);

            recordConsumer.endField(name, fieldIndex);
        }

        private void writeList(String name, List<?> values) {
            int fieldIndex = schema.getFieldIndex(name);
            PrimitiveType type = schema.getType(fieldIndex).asPrimitiveType();
            recordConsumer.startField(name, fieldIndex);
            for (var value : values) {
                writeValue(type, value);
            }
            recordConsumer.endField(name, fieldIndex);
        }

        private void writeList(String name, TLongList values) {
            int fieldIndex = schema.getFieldIndex(name);
            PrimitiveType type = schema.getType(fieldIndex).asPrimitiveType();
            recordConsumer.startField(name, fieldIndex);

            for (int i = 0; i < values.size(); i++) {
                writeValue(type, values.get(i));
            }

            recordConsumer.endField(name, fieldIndex);
        }

        private void writeList(String name, TIntList values) {
            int fieldIndex = schema.getFieldIndex(name);
            PrimitiveType type = schema.getType(fieldIndex).asPrimitiveType();
            recordConsumer.startField(name, fieldIndex);

            for (int i = 0; i < values.size(); i++) {
                writeValue(type, values.get(i));
            }

            recordConsumer.endField(name, fieldIndex);
        }

        void writeValue(PrimitiveType type, long value) {
            assert type.getPrimitiveTypeName() == INT64;
            recordConsumer.addLong(value);
        }

        void writeValue(PrimitiveType type, int value) {
            assert type.getPrimitiveTypeName() == INT32;
            recordConsumer.addInteger(value);
        }

        void writeValue(PrimitiveType type, Object value) {
            switch (type.getPrimitiveTypeName()) {
                case INT32: recordConsumer.addInteger((int)value); break;
                case INT64: recordConsumer.addLong((long)value); break;
                case DOUBLE: recordConsumer.addDouble((double)value); break;
                case BOOLEAN: recordConsumer.addBoolean((boolean)value); break;
                case FLOAT: recordConsumer.addFloat((float)value); break;
                case BINARY:
                    if (type.getLogicalTypeAnnotation() == LogicalTypeAnnotation.stringType()) {
                        recordConsumer.addBinary(Binary.fromString((String)value));
                    } else {
                        recordConsumer.addBinary(Binary.fromConstantByteArray((byte[])value));
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("We don't support writing " + type.getPrimitiveTypeName());
            }

        }
    }
}
