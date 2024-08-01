package nu.marginalia.io.crawlspec;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;

import java.io.IOException;
import java.nio.file.Path;

public class CrawlSpecRecordParquetFileWriter implements AutoCloseable {
    private final ParquetWriter<CrawlSpecRecord> writer;

    public CrawlSpecRecordParquetFileWriter(Path file) throws IOException {
        writer = ParquetWriter.writeFile(CrawlSpecRecord.schema,
                file.toFile(), CrawlSpecRecord.newDehydrator());
    }

    public void write(CrawlSpecRecord domainData) throws IOException {
        writer.write(domainData);
    }

    public void close() throws IOException {
        writer.close();
    }
}
