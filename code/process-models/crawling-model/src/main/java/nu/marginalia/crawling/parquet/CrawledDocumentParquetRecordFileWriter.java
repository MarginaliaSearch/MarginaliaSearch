package nu.marginalia.crawling.parquet;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.crawling.body.DocumentBodyExtractor;
import nu.marginalia.crawling.body.DocumentBodyResult;
import nu.marginalia.crawling.body.HttpFetchResult;
import org.netpreserve.jwarc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

public class CrawledDocumentParquetRecordFileWriter implements AutoCloseable {
    private final ParquetWriter<CrawledDocumentParquetRecord> writer;
    private static final Logger logger = LoggerFactory.getLogger(CrawledDocumentParquetRecordFileWriter.class);

    public static void convertWarc(String domain, Path warcInputFile, Path parquetOutputFile) {
        try (var warcReader = new WarcReader(warcInputFile);
             var parquetWriter = new CrawledDocumentParquetRecordFileWriter(parquetOutputFile)
        ) {
            WarcXResponseReference.register(warcReader);
            WarcXEntityRefused.register(warcReader);

            for (var record : warcReader) {
                if (record instanceof WarcResponse response) {
                    // this also captures WarcXResponseReference, which inherits from WarcResponse
                    // and is used to store old responses from previous crawls; in this part of the logic
                    // we treat them the same as a normal response

                    parquetWriter.write(domain, response);
                }
                else if (record instanceof WarcXEntityRefused refused) {
                    parquetWriter.write(domain, refused);
                }
                else if (record instanceof Warcinfo warcinfo) {
                    parquetWriter.write(warcinfo);
                }
            }
        }
        catch (Exception ex) {
            logger.error("Failed to convert WARC file to Parquet", ex);
        }
    }

    private void write(String domain, WarcXEntityRefused refused) throws IOException {
        URI profile = refused.profile();

        String meta;
        if (profile.equals(WarcXEntityRefused.documentRobotsTxtSkippedURN)) {
            meta = "x-marginalia/advisory;state=robots-txt-skipped";
        }
        else if (profile.equals(WarcXEntityRefused.documentBadContentTypeURN)) {
            meta = "x-marginalia/advisory;state=content-type-failed-probe";
        }
        else if (profile.equals(WarcXEntityRefused.documentProbeTimeout)) {
            meta = "x-marginalia/advisory;state=timeout-probe";
        }
        else if (profile.equals(WarcXEntityRefused.documentUnspecifiedError)) {
            meta = "x-marginalia/advisory;state=doc-error";
        }
        else {
            meta = "x-marginalia/advisory;state=unknown";
        }

        write(forDocError(domain, refused.date(), refused.target(), meta));
    }

    private void write(Warcinfo warcinfo) throws IOException {
        String selfDomain = warcinfo.fields().first("domain").orElse("");
        String ip = warcinfo.fields().first("ip").orElse("");
        String probeStatus = warcinfo.fields().first("X-WARC-Probe-Status").orElse("");

        if (probeStatus.startsWith("REDIRECT")) {
            String redirectDomain = probeStatus.substring("REDIRECT;".length());
            write(forDomainRedirect(selfDomain, warcinfo.date(), redirectDomain));
        }
        else if (!"OK".equals(probeStatus)) {
            write(forDomainError(selfDomain, warcinfo.date(), ip, probeStatus));
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

        // We don't want to store robots.txt files, as they are not
        // interesting for the analysis we want to do.  This is important
        // since txt-files in general are interesting, and we don't want to
        // exclude them as a class.

        if (fetchOk.uri().getPath().equals("/robots.txt")) {
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
                WarcXCookieInformationHeader.hasCookies(response),
                fetchOk.statusCode(),
                response.date(),
                contentType,
                bodyBytes)
        );
    }


    public void close() throws IOException {
        writer.close();
    }

    private CrawledDocumentParquetRecord forDomainRedirect(String domain, Instant date, String redirectDomain) {
        return new CrawledDocumentParquetRecord(domain,
                STR."https://\{redirectDomain}/",
                "",
                false,
                0,
                date,
                "x-marginalia/advisory;state=redirect",
                new byte[0]
        );
    }
    private CrawledDocumentParquetRecord forDomainError(String domain, Instant date, String ip, String errorStatus) {
        return new CrawledDocumentParquetRecord(domain,
                STR."https://\{domain}/",
                ip,
                false,
                0,
                date,
                "x-marginalia/advisory;state=error",
                errorStatus.getBytes()
        );
    }

    private CrawledDocumentParquetRecord forDocError(String domain, Instant date, String url, String errorStatus) {
        return new CrawledDocumentParquetRecord(domain,
                url,
                "",
                false,
                0,
                date,
                errorStatus,
                new byte[0]
        );
    }
}
