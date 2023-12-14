package nu.marginalia.crawling.parquet;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.crawling.body.DocumentBodyExtractor;
import nu.marginalia.crawling.body.DocumentBodyResult;
import nu.marginalia.crawling.body.HttpFetchResult;
import org.netpreserve.jwarc.*;
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
                if (record instanceof WarcResponse response) {
                    parquetWriter.write(domain, response);
                }
                else if (record instanceof Warcinfo warcinfo) {
                    parquetWriter.write(domain, warcinfo);
                }
                else {
                    logger.warn("Skipping record of type {}", record.type());
                }

            }
        }
        catch (Exception ex) {
            logger.error("Failed to convert WARC file to Parquet", ex);
        }
    }

    private void write(String domain, Warcinfo warcinfo) throws IOException {
        String selfDomain = warcinfo.fields().first("domain").orElse("");
        String ip = warcinfo.fields().first("ip").orElse("");
        String probeStatus = warcinfo.fields().first("X-WARC-Probe-Status").orElse("");

        if (probeStatus.startsWith("REDIRECT")) {
            String redirectDomain = probeStatus.substring("REDIRECT;".length());
            write(new CrawledDocumentParquetRecord(selfDomain,
                    STR."https://\{redirectDomain}/",
                    ip,
                    false,
                    0,
                    "x-marginalia/advisory;state=redirect",
                    new byte[0]
            ));
        }
        else if (!"OK".equals(probeStatus)) {
            write(new CrawledDocumentParquetRecord(selfDomain,
                    STR."https://\{domain}/",
                    ip,
                    false,
                    0,
                    "x-marginalia/advisory;state=error",
                    probeStatus.getBytes()
            ));
        }
    }

    public CrawledDocumentParquetRecordFileWriter(Path file) throws IOException {
        writer = ParquetWriter.writeFile(CrawledDocumentParquetRecord.schema,
                file.toFile(), CrawledDocumentParquetRecord.newDehydrator());
    }

    public void write(CrawledDocumentParquetRecord domainData) throws IOException {
        writer.write(domainData);
    }

    public void write(String domain, WarcResponse response) throws IOException {

        HttpFetchResult result = HttpFetchResult.importWarc(response);
        if (!(result instanceof HttpFetchResult.ResultOk fetchOk)) {
            return;
        }

        byte[] bodyBytes;
        String contentType;

        var body = DocumentBodyExtractor.asBytes(result);

        if (body instanceof DocumentBodyResult.Ok<byte[]> bodyOk) {
            bodyBytes = bodyOk.body();
            contentType = bodyOk.contentType().toString();
        }
        else {
            bodyBytes = new byte[0];
            contentType = "";
        }

        write(new CrawledDocumentParquetRecord(
                domain,
                response.target(),
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
