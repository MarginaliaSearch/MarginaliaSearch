package nu.marginalia.io.processed;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.model.processed.DocumentRecord;

import java.io.IOException;
import java.nio.file.Path;

public class DocumentRecordParquetFileWriter implements AutoCloseable {
    private final ParquetWriter<DocumentRecord> writer;

    public DocumentRecordParquetFileWriter(Path file) throws IOException {
        writer = ParquetWriter.writeFile(DocumentRecord.schema,
                file.toFile(), DocumentRecord.newDehydrator());
    }

    public void write(DocumentRecord domainData) throws IOException {
        writer.write(domainData);
    }

    public void close() throws IOException {
        writer.close();
    }
}
