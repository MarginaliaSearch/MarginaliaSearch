package nu.marginalia.io.processed;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.model.processed.DomainLinkRecord;

import java.io.IOException;
import java.nio.file.Path;

public class DomainLinkRecordParquetFileWriter implements AutoCloseable {
    private final ParquetWriter<DomainLinkRecord> writer;

    public DomainLinkRecordParquetFileWriter(Path file) throws IOException {
        writer = ParquetWriter.writeFile(DomainLinkRecord.schema,
                file.toFile(), DomainLinkRecord.newDehydrator());
    }

    public void write(DomainLinkRecord domainData) throws IOException {
        writer.write(domainData);
    }

    public void close() throws IOException {
        writer.close();
    }
}
