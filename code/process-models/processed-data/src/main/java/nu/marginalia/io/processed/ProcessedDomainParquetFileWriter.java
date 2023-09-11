package nu.marginalia.io.processed;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.codec.processed.ProcessedDomainDataDehydrator;
import nu.marginalia.model.processed.ProcessedDomainData;

import java.io.IOException;
import java.nio.file.Path;

public class ProcessedDomainParquetFileWriter implements AutoCloseable {
    private final ParquetWriter<ProcessedDomainData> writer;

    public ProcessedDomainParquetFileWriter(Path file) throws IOException {
        writer = ParquetWriter.writeFile(ProcessedDomainData.schema,
                file.toFile(), new ProcessedDomainDataDehydrator());
    }

    public void write(ProcessedDomainData domainData) throws IOException {
        writer.write(domainData);
    }

    public void close() throws IOException {
        writer.close();
    }
}
