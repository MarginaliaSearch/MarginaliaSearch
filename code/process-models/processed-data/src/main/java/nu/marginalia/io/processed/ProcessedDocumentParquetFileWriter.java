package nu.marginalia.io.processed;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.codec.processed.ProcessedDocumentDataDehydrator;
import nu.marginalia.model.processed.ProcessedDocumentData;

import java.io.IOException;
import java.nio.file.Path;

public class ProcessedDocumentParquetFileWriter implements AutoCloseable {
    private final ParquetWriter<ProcessedDocumentData> writer;

    public ProcessedDocumentParquetFileWriter(Path file) throws IOException {
        writer = ParquetWriter.writeFile(ProcessedDocumentData.schema,
                file.toFile(), new ProcessedDocumentDataDehydrator());
    }

    public void write(ProcessedDocumentData domainData) throws IOException {
        writer.write(domainData);
    }

    public void close() throws IOException {
        writer.close();
    }
}
