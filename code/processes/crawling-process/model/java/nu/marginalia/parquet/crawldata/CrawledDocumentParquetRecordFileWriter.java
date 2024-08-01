package nu.marginalia.parquet.crawldata;

import blue.strategic.parquet.ParquetWriter;
import nu.marginalia.UserAgent;
import nu.marginalia.model.body.DocumentBodyExtractor;
import nu.marginalia.model.body.DocumentBodyResult;
import nu.marginalia.model.body.HttpFetchResult;
import org.apache.commons.lang3.StringUtils;
import org.netpreserve.jwarc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class CrawledDocumentParquetRecordFileWriter implements AutoCloseable {
    private final ParquetWriter<CrawledDocumentParquetRecord> writer;
    private static final Logger logger = LoggerFactory.getLogger(CrawledDocumentParquetRecordFileWriter.class);

    public static void convertWarc(String domain,
                                   UserAgent userAgent,
                                   Path warcInputFile,
                                   Path parquetOutputFile) {
        try (var warcReader = new WarcReader(warcInputFile);
             var parquetWriter = new CrawledDocumentParquetRecordFileWriter(parquetOutputFile)
        ) {
            WarcXResponseReference.register(warcReader);
            WarcXEntityRefused.register(warcReader);

            String uaString = userAgent.uaString();

            for (var record : warcReader) {
                try {
                    if (record instanceof WarcResponse response) {
                        // this also captures WarcXResponseReference, which inherits from WarcResponse
                        // and is used to store old responses from previous crawls; in this part of the logic
                        // we treat them the same as a normal response

                        if (!filterResponse(uaString, response)) {
                            continue;
                        }

                        parquetWriter.write(domain, response);
                    } else if (record instanceof WarcXEntityRefused refused) {
                        parquetWriter.write(domain, refused);
                    } else if (record instanceof Warcinfo warcinfo) {
                        parquetWriter.write(warcinfo);
                    }
                }
                catch (Exception ex) {
                    logger.error("Failed to convert WARC record to Parquet", ex);
                }
            }
        }
        catch (Exception ex) {
            logger.error("Failed to convert WARC file to Parquet", ex);
        }
    }

    /** Return true if the WarcResponse should be excluded from conversion */
    private static boolean filterResponse(String uaString, WarcResponse response) throws IOException {

        // We don't want to store robots.txt files, as they are not
        // interesting for the analysis we want to do.  This is important
        // since txt-files in general are interesting, and we don't want to
        // exclude them as a class.

        if (response.targetURI().getPath().equals("/robots.txt")) {
            return false;
        }

        var robotsTags = response.http().headers().all("X-Robots-Tag");
        if (!isXRobotsTagsPermitted(robotsTags, uaString)) {
            return false;
        }

        return true;
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

        byte[] bodyBytes;
        String contentType;

        var body = DocumentBodyExtractor.asBytes(result);

        var headers = fetchOk.headers();

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
                bodyBytes,
                headers.get("ETag"),
                headers.get("Last-Modified"))
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
                new byte[0],
                null,
                null
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
                errorStatus.getBytes(),
                null,
                null
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
                new byte[0],
                null,
                null
        );
    }


    /**  Check X-Robots-Tag header tag to see if we are allowed to index this page.
     * <p>
     * Reference: <a href="https://developers.google.com/search/docs/crawling-indexing/robots-meta-tag">https://developers.google.com/search/docs/crawling-indexing/robots-meta-tag</a>
     *
     * @param xRobotsHeaderTags List of X-Robots-Tag values
     * @param userAgent User agent string
     * @return true if we are allowed to index this page
     */
    // Visible for tests
    public static boolean isXRobotsTagsPermitted(List<String> xRobotsHeaderTags, String userAgent) {
        boolean isPermittedGeneral = true;
        boolean isPermittedMarginalia = false;
        boolean isForbiddenMarginalia = false;

        for (String header : xRobotsHeaderTags) {
            if (header.indexOf(':') >= 0) {
                String[] parts = StringUtils.split(header, ":", 2);

                if (parts.length < 2)
                    continue;

                // Is this relevant to us?
                if (!Objects.equals(parts[0].trim(), userAgent))
                    continue;

                if (parts[1].contains("noindex"))
                    isForbiddenMarginalia = true;
                else if (parts[1].contains("none"))
                    isForbiddenMarginalia = true;
                else if (parts[1].contains("all"))
                    isPermittedMarginalia = true;
            }
            else {
                if (header.contains("noindex"))
                    isPermittedGeneral = false;
                if (header.contains("none"))
                    isPermittedGeneral = false;
            }
        }

        if (isPermittedMarginalia)
            return true;
        if (isForbiddenMarginalia)
            return false;
        return isPermittedGeneral;
    }
}
