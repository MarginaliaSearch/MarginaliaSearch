package nu.marginalia.io.processed;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.model.processed.DomainRecord;

import java.io.IOException;
import java.nio.file.Path;

public class DomainRecordParquetFileWriter implements AutoCloseable {
    private final ParquetWriter<DomainRecord> writer;

    public DomainRecordParquetFileWriter(Path file) throws IOException {
        writer = ParquetWriter.writeFile(DomainRecord.schema,
                file.toFile(), DomainRecord.newDehydrator());
    }

    public void write(DomainRecord domainData) throws IOException {
        writer.write(domainData);
    }

    public void close() throws IOException {
        writer.close();
    }
}
