package nu.marginalia.crawling.parquet;

import blue.strategic.parquet.ParquetWriter;

import java.io.IOException;
import java.nio.file.Path;

public class CrawledDocumentParquetRecordFileWriter implements AutoCloseable {
    private final ParquetWriter<CrawledDocumentParquetRecord> writer;

    public CrawledDocumentParquetRecordFileWriter(Path file) throws IOException {
        writer = ParquetWriter.writeFile(CrawledDocumentParquetRecord.schema,
                file.toFile(), CrawledDocumentParquetRecord.newDehydrator());
    }

    public void write(CrawledDocumentParquetRecord domainData) throws IOException {
        writer.write(domainData);
    }

    public void close() throws IOException {
        writer.close();
    }
}
