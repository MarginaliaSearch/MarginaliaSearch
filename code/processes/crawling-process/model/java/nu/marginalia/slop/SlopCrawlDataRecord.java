package nu.marginalia.slop;

import nu.marginalia.ContentTypes;
import nu.marginalia.UserAgent;
import nu.marginalia.model.body.DocumentBodyExtractor;
import nu.marginalia.model.body.DocumentBodyResult;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.primitive.ByteColumn;
import nu.marginalia.slop.column.primitive.IntColumn;
import nu.marginalia.slop.column.primitive.LongColumn;
import nu.marginalia.slop.column.primitive.ShortColumn;
import nu.marginalia.slop.column.string.EnumColumn;
import nu.marginalia.slop.column.string.StringColumn;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.LargeItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.netpreserve.jwarc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.IDN;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public record SlopCrawlDataRecord(String domain,
                                  String url,
                                  String ip,
                                  boolean cookies,
                                  int httpStatus,
                                  long timestamp,
                                  String contentType,
                                  byte[] body,
                                  int requestTimeMs,
                                  String headers)
{
    private static final EnumColumn domainColumn = new EnumColumn("domain", StandardCharsets.UTF_8, StorageType.ZSTD);
    private static final StringColumn urlColumn = new StringColumn("url", StandardCharsets.UTF_8, StorageType.ZSTD);
    private static final StringColumn ipColumn = new StringColumn("ip", StandardCharsets.ISO_8859_1, StorageType.ZSTD);
    private static final ByteColumn cookiesColumn = new ByteColumn("cookies");
    private static final ShortColumn statusColumn = new ShortColumn("httpStatus");
    private static final LongColumn timestampColumn = new LongColumn("timestamp");
    private static final EnumColumn contentTypeColumn = new EnumColumn("contentType", StandardCharsets.UTF_8);
    private static final ByteArrayColumn bodyColumn = new ByteArrayColumn("body", StorageType.ZSTD);
    private static final ShortColumn requestTimeColumn = new ShortColumn("requestTimeMs");
    private static final StringColumn headerColumn = new StringColumn("header", StandardCharsets.UTF_8, StorageType.ZSTD);

    private static SlopCrawlDataRecord forDomainRedirect(String domain, Instant date, String redirectDomain) {
        return new SlopCrawlDataRecord(domain,
                "https://" + redirectDomain + "/",
                "",
                false,
                0,
                date.toEpochMilli(),
                "x-marginalia/advisory;state=redirect",
                new byte[0],
                -1,
                ""
        );
    }

    private static SlopCrawlDataRecord forDomainError(String domain, Instant date, String ip, String errorStatus) {
        return new SlopCrawlDataRecord(domain,
                "https://" + domain + "/",
                ip,
                false,
                0,
                date.toEpochMilli(),
                "x-marginalia/advisory;state=error",
                errorStatus.getBytes(),
                -1,
                ""
        );
    }

    private static SlopCrawlDataRecord forDocError(String domain, Instant date, String url, String errorStatus) {
        return new SlopCrawlDataRecord(domain,
                url,
                "",
                false,
                0,
                date.toEpochMilli(),
                errorStatus,
                new byte[0],
                -1,
                ""
        );
    }


    private static final Logger logger = LoggerFactory.getLogger(SlopCrawlDataRecord.class);

    public static void convertWarc(String domain,
                                   UserAgent userAgent,
                                   Path warcInputFile,
                                   Path slopOutputFile) throws IOException {

        Path tempDir = Files.createTempDirectory(slopOutputFile.getParent(), "slop-"+ IDN.toASCII(domain));

        try (var warcReader = new WarcReader(warcInputFile);
             var slopWriter = new SlopCrawlDataRecord.Writer(tempDir)
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

                        var filterStatus = filterResponse(uaString, response);
                        if (filterStatus.isRejected()) {
                            continue;
                        }

                        slopWriter.write(domain, filterStatus, response);
                    } else if (record instanceof WarcXEntityRefused refused) {
                        slopWriter.write(domain, refused);
                    } else if (record instanceof Warcinfo warcinfo) {
                        slopWriter.write(warcinfo);
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

        try {
            SlopTablePacker.packToSlopZip(tempDir, slopOutputFile);
            FileUtils.deleteDirectory(tempDir.toFile());
        }
        catch (Exception ex) {
            logger.error("Failed to convert WARC file to Parquet", ex);
        }
    }

    sealed interface ResponseFilterResult {
        default boolean isRejected() { return false; }
        record Accept() implements ResponseFilterResult {}
        record AcceptWithContentType(String contentType) implements ResponseFilterResult {}
        record AcceptIfPlainText(String contentType) implements ResponseFilterResult {}
        record Reject() implements ResponseFilterResult {
            @Override
            public boolean isRejected() { return true; }
        }
    }

    /** Return true if the WarcResponse should be excluded from conversion */
    private static ResponseFilterResult filterResponse(String uaString, WarcResponse response) throws IOException {

        // We don't want to store robots.txt files, as they are not
        // interesting for the analysis we want to do.  This is important
        // since txt-files in general are interesting, and we don't want to
        // exclude them as a class.

        String uriPath = response.targetURI().getPath();
        if (uriPath.equals("/robots.txt")) {
            return new ResponseFilterResult.Reject();
        }

        var headers = response.http().headers();
        var robotsTags = headers.all("X-Robots-Tag");

        if (!isXRobotsTagsPermitted(robotsTags, uaString)) {
            return new ResponseFilterResult.Reject();
        }

        // Strip out responses with content types we aren't interested in
        // (though ideally we wouldn't download these at all)
        String contentType = headers.first("Content-Type").orElse("text/plain").toLowerCase();

        if (!ContentTypes.isAccepted(contentType)) {
            String contentTypeWithoutParams = StringUtils.substringBefore(contentType, ";");

            // Some servers don't understand what a markdown file is
            if (contentTypeWithoutParams.equals("application/octet-stream")) {
                if (uriPath.endsWith(".md")) {
                    // This is a markdown file, which we want to keep
                    return new ResponseFilterResult.AcceptIfPlainText("text/markdown");
                }
                else if (uriPath.endsWith(".pdf")) {
                    // This is a text file, which we want to keep
                    return new ResponseFilterResult.AcceptWithContentType("application/pdf");
                }
            }

            return new ResponseFilterResult.Reject();
        }

        // If the format is binary, we don't want to translate it if the response is truncated
        if (response.truncated() != WarcTruncationReason.NOT_TRUNCATED && ContentTypes.isBinary(contentType)) {
            return new ResponseFilterResult.Reject();
        }

        return new ResponseFilterResult.Accept();
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

    public static int countGoodStatusCodes(Path path) throws IOException {
        int cnt = 0;

        try (var table = new SlopTable(path)) {
            ShortColumn.Reader statusReader = statusColumn.open(table);
            while (statusReader.hasRemaining()) {
                int status = statusReader.get();
                if (status == 200 || status == 206) {
                    cnt++;
                }
            }
        }

        return cnt;
    }

    public static class Writer extends SlopTable {
        private final EnumColumn.Writer domainColumnWriter;
        private final StringColumn.Writer urlColumnWriter;
        private final StringColumn.Writer ipColumnWriter;
        private final ByteColumn.Writer cookiesColumnWriter;
        private final ShortColumn.Writer statusColumnWriter;
        private final LongColumn.Writer timestampColumnWriter;
        private final EnumColumn.Writer contentTypeColumnWriter;
        private final ByteArrayColumn.Writer bodyColumnWriter;
        private final ShortColumn.Writer requestTimeColumnWriter;
        private final StringColumn.Writer headerColumnWriter;

        public Writer(Path path) throws IOException {
            super(path);

            domainColumnWriter = domainColumn.create(this);
            urlColumnWriter = urlColumn.create(this);
            ipColumnWriter = ipColumn.create(this);
            cookiesColumnWriter = cookiesColumn.create(this);
            statusColumnWriter = statusColumn.create(this);
            timestampColumnWriter = timestampColumn.create(this);
            contentTypeColumnWriter = contentTypeColumn.create(this);
            bodyColumnWriter = bodyColumn.create(this);
            requestTimeColumnWriter = requestTimeColumn.create(this);
            headerColumnWriter = headerColumn.create(this);
        }

        public void write(SlopCrawlDataRecord record) throws IOException {
            domainColumnWriter.put(record.domain);
            urlColumnWriter.put(record.url);
            ipColumnWriter.put(record.ip);
            cookiesColumnWriter.put(record.cookies ? (byte) 1 : (byte) 0);
            statusColumnWriter.put((short) record.httpStatus);
            timestampColumnWriter.put(record.timestamp);
            contentTypeColumnWriter.put(record.contentType);
            bodyColumnWriter.put(record.body);
            requestTimeColumnWriter.put((short) record.requestTimeMs);
            headerColumnWriter.put(record.headers);
        }

        public void write(String domain, ResponseFilterResult filterStatus, WarcResponse response) throws IOException {

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

            switch (filterStatus) {
                case ResponseFilterResult.AcceptWithContentType(String ct) -> contentType = ct;
                case ResponseFilterResult.AcceptIfPlainText(String ct) -> {
                    try {
                        // Parse the body as UTF-8
                        new String(bodyBytes, StandardCharsets.UTF_8);
                        contentType = ct;
                    }
                    catch (RuntimeException ex) { // UTF-8 decoding failed
                        return;
                    }
                }
                default -> {}
            }

            boolean hasCookies = false;

            String headersStr;
            StringJoiner headersStrBuilder = new StringJoiner("\n");
            int requestTimeMs = -1;
            for (var header : headers) {
                if (header.getName().equalsIgnoreCase("X-Cookies") && "1".equals(header.getValue())) {
                    hasCookies = true;
                }
                if (header.getName().equals("X-Marginalia-Response-Time")) {
                    try {
                        requestTimeMs = Integer.parseInt(header.getValue());
                    }
                    catch (NumberFormatException ex) {
                        logger.warn("Failed to parse X-Marginalia-Response-Time header: {}", header.getValue());
                    }
                    continue;
                }
                headersStrBuilder.add(header.getName() + ": " + header.getValue());
            }
            headersStr = headersStrBuilder.toString();


            write(new SlopCrawlDataRecord(
                    domain,
                    response.target(),
                    fetchOk.ipAddress(),
                    hasCookies,
                    fetchOk.statusCode(),
                    response.date().toEpochMilli(),
                    contentType,
                    bodyBytes,
                    requestTimeMs,
                    headersStr
                )
            );
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
    }

    public static class Reader extends SlopTable {
        private final EnumColumn.Reader domainColumnReader;
        private final StringColumn.Reader urlColumnReader;
        private final StringColumn.Reader ipColumnReader;
        private final ByteColumn.Reader cookiesColumnReader;
        private final ShortColumn.Reader statusColumnReader;
        private final LongColumn.Reader timestampColumnReader;
        private final EnumColumn.Reader contentTypeColumnReader;
        private final ByteArrayColumn.Reader bodyColumnReader;
        private final ShortColumn.Reader requestTimeColumnReader;
        private final StringColumn.Reader headerColumnReader;

        public Reader(Path path) throws IOException {
            super(path);

            domainColumnReader = domainColumn.open(this);
            urlColumnReader = urlColumn.open(this);
            ipColumnReader = ipColumn.open(this);
            cookiesColumnReader = cookiesColumn.open(this);
            statusColumnReader = statusColumn.open(this);
            timestampColumnReader = timestampColumn.open(this);
            contentTypeColumnReader = contentTypeColumn.open(this);
            bodyColumnReader = bodyColumn.open(this);
            headerColumnReader = headerColumn.open(this);

            // FIXME: After 2025-06-XX, we can remove this migration workaround
            ShortColumn.Reader timeColumnReader;
            try {
                timeColumnReader = requestTimeColumn.open(this);
            }
            catch (Exception ex) {
                // Migration workaround
                timeColumnReader = null;
            }
            requestTimeColumnReader = timeColumnReader;
        }

        public SlopCrawlDataRecord get() throws IOException {
            return new SlopCrawlDataRecord(
                    domainColumnReader.get(),
                    urlColumnReader.get(),
                    ipColumnReader.get(),
                    cookiesColumnReader.get() == 1,
                    statusColumnReader.get(),
                    timestampColumnReader.get(),
                    contentTypeColumnReader.get(),
                    bodyColumnReader.get(),
                    requestTimeColumnReader != null ? requestTimeColumnReader.get() : -1,
                    headerColumnReader.get()
            );
        }

        public boolean hasRemaining() throws IOException {
            return domainColumnReader.hasRemaining();
        }
    }


    public abstract static class FilteringReader extends SlopTable {
        private final EnumColumn.Reader domainColumnReader;
        private final StringColumn.Reader urlColumnReader;
        private final StringColumn.Reader ipColumnReader;
        private final ByteColumn.Reader cookiesColumnReader;
        private final ShortColumn.Reader statusColumnReader;
        private final LongColumn.Reader timestampColumnReader;
        private final EnumColumn.Reader contentTypeColumnReader;
        private final ByteArrayColumn.Reader bodyColumnReader;
        private final ShortColumn.Reader requestTimeColumnReader;
        private final StringColumn.Reader headerColumnReader;

        private SlopCrawlDataRecord next = null;

        public FilteringReader(Path path) throws IOException {
            super(path);

            domainColumnReader = domainColumn.open(this);
            urlColumnReader = urlColumn.open(this);
            ipColumnReader = ipColumn.open(this);
            cookiesColumnReader = cookiesColumn.open(this);
            statusColumnReader = statusColumn.open(this);
            timestampColumnReader = timestampColumn.open(this);
            contentTypeColumnReader = contentTypeColumn.open(this);
            bodyColumnReader = bodyColumn.open(this);
            headerColumnReader = headerColumn.open(this);

            // FIXME: After 2025-06-XX, we can remove this migration workaround
            ShortColumn.Reader timeColumnReader;
            try {
                timeColumnReader = requestTimeColumn.open(this);
            }
            catch (Exception ex) {
                // Migration workaround
                timeColumnReader = null;
            }
            requestTimeColumnReader = timeColumnReader;
        }

        public abstract boolean filter(String url, int status, String contentType);

        public SlopCrawlDataRecord get() throws IOException {
            if (next == null) {
                if (!hasRemaining()) {
                    throw new IllegalStateException("No more values remaining");
                }
            }
            var val = next;
            next = null;
            return val;
        }

        public boolean hasRemaining() throws IOException {
            if (next != null)
                return true;

            while (domainColumnReader.hasRemaining()) {
                String domain = domainColumnReader.get();
                String url = urlColumnReader.get();
                String ip = ipColumnReader.get();
                boolean cookies = cookiesColumnReader.get() == 1;
                int status = statusColumnReader.get();
                long timestamp = timestampColumnReader.get();
                int requestTimeMs = requestTimeColumnReader != null ? requestTimeColumnReader.get() : -1;
                String contentType = contentTypeColumnReader.get();

                LargeItem<byte[]> body = bodyColumnReader.getLarge();
                LargeItem<String> headers = headerColumnReader.getLarge();

                if (filter(url, status, contentType)) {
                    next = new SlopCrawlDataRecord(
                            domain, url, ip, cookies, status, timestamp, contentType, body.get(), requestTimeMs, headers.get()
                    );
                    return true;
                }
                else {
                    body.close();
                    headers.close();
                }
            }

            return false;
        }
    }
}
