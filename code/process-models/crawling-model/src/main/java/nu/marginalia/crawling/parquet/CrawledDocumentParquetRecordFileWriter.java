package nu.marginalia.crawling.parquet;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.crawling.body.DocumentBodyExtractor;
import nu.marginalia.crawling.body.DocumentBodyResult;
import nu.marginalia.crawling.body.HttpFetchResult;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRecord;
import org.netpreserve.jwarc.WarcResponse;
import org.netpreserve.jwarc.WarcXResponseReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class CrawledDocumentParquetRecordFileWriter implements AutoCloseable {
    private final ParquetWriter<CrawledDocumentParquetRecord> writer;
    private static final Logger logger = LoggerFactory.getLogger(CrawledDocumentParquetRecordFileWriter.class);

    public static void convertWarc(String domain, Path warcInputFile, Path parquetOutputFile) throws IOException {
        try (var warcReader = new WarcReader(warcInputFile);
             var parquetWriter = new CrawledDocumentParquetRecordFileWriter(parquetOutputFile)
        ) {
            WarcXResponseReference.register(warcReader);

            for (var record : warcReader) {
                parquetWriter.write(domain, record);
            }
        }
        catch (Exception ex) {
            logger.error("Failed to convert WARC file to Parquet", ex);
        }
    }

    public CrawledDocumentParquetRecordFileWriter(Path file) throws IOException {
        writer = ParquetWriter.writeFile(CrawledDocumentParquetRecord.schema,
                file.toFile(), CrawledDocumentParquetRecord.newDehydrator());
    }

    public void write(CrawledDocumentParquetRecord domainData) throws IOException {
        writer.write(domainData);
    }

    public void write(String domain, WarcRecord record) throws IOException {
        if (!(record instanceof WarcResponse ref)) {
            return;
        }

        HttpFetchResult result = HttpFetchResult.importWarc(ref);
        if (!(result instanceof HttpFetchResult.ResultOk fetchOk)) {
            return;
        }

        byte[] bodyBytes;
        String contentType;

        var body = DocumentBodyExtractor.asBytes(result);

        if (body instanceof DocumentBodyResult.Ok<byte[]> bodyOk) {
            bodyBytes = bodyOk.body();
            contentType = bodyOk.contentType();
        }
        else {
            bodyBytes = new byte[0];
            contentType = "";
        }

        write(new CrawledDocumentParquetRecord(
                domain,
                ref.target(),
                fetchOk.ipAddress(),
                false, // FIXME
                fetchOk.statusCode(),
                contentType,
                bodyBytes)
        );
    }


    public void close() throws IOException {
        writer.close();
    }
}
