package nu.marginalia.crawling.io.format;

import lombok.SneakyThrows;
import nu.marginalia.crawling.body.DocumentBodyExtractor;
import nu.marginalia.crawling.body.DocumentBodyResult;
import nu.marginalia.crawling.body.HttpFetchResult;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;
import org.netpreserve.jwarc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class WarcSerializableCrawlDataStream implements AutoCloseable, SerializableCrawlDataStream {
    private static final Logger logger = LoggerFactory.getLogger(WarcSerializableCrawlDataStream.class);

    private final WarcReader reader;
    private final Iterator<WarcRecord> backingIterator;
    private SerializableCrawlData next = null;
    private final Path path;

    public WarcSerializableCrawlDataStream(Path file) throws IOException {
        path = file;
        reader = new WarcReader(file);
        WarcXResponseReference.register(reader);
        WarcXEntityRefused.register(reader);

        backingIterator = reader.iterator();
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    @SneakyThrows
    public boolean hasNext() {
        while (backingIterator.hasNext() && next == null) {
            var nextRecord = backingIterator.next();
            if (nextRecord instanceof WarcResponse response) { // this also includes WarcXResponseReference
                convertResponse(response);
            }
            else if (nextRecord instanceof Warcinfo warcinfo) {
                convertWarcinfo(warcinfo);
            }
        }
        return next != null;
    }

    private void convertWarcinfo(Warcinfo warcinfo) throws IOException {
        var headers = warcinfo.fields();
        String probeStatus = headers.first("X-WARC-Probe-Status").orElse("");
        String[] parts = probeStatus.split(" ", 2);


        String domain = headers.first("domain").orElseThrow(() -> new IllegalStateException("Missing domain header"));
        String status = parts[0];
        String statusReason = parts.length > 1 ? parts[1] : "";
        String ip = headers.first("ip").orElse("");

        String redirectDomain = null;
        if ("REDIRECT".equalsIgnoreCase(status)) {
            redirectDomain = statusReason;
        }

        next = new CrawledDomain(domain, redirectDomain, status, statusReason, ip,
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    private void convertResponse(WarcResponse response) throws IOException {
        var http = response.http();

        if (http.status() != 200) {
            return;
        }

        var httpHeaders = http.headers();

        var parsedBody = DocumentBodyExtractor.asString(HttpFetchResult.importWarc(response));
        if (parsedBody instanceof DocumentBodyResult.Error<String> error) {
            next = new CrawledDocument(
                    "",
                    response.targetURI().toString(),
                    http.contentType().raw(),
                    response.date().toString(),
                    http.status(),
                    error.status().toString(),
                    error.why(),
                    headers(http.headers()),
                    null,
                    response.payloadDigest().map(WarcDigest::base64).orElse(""),
                    "",
                    "",
                    "",
                    WarcXCookieInformationHeader.hasCookies(response),
                    null,
                    null
            );
        } else if (parsedBody instanceof DocumentBodyResult.Ok<String> ok) {
            next = new CrawledDocument(
                    "",
                    response.targetURI().toString(),
                    ok.contentType().toString(),
                    response.date().toString(),
                    http.status(),
                    "OK",
                    "",
                    headers(http.headers()),
                    ok.body(),
                    response.payloadDigest().map(WarcDigest::base64).orElse(""),
                    "",
                    "",
                    "",
                    WarcXCookieInformationHeader.hasCookies(response),
                    httpHeaders.first("Last-Modified").orElse(""),
                    httpHeaders.first("ETag").orElse(""));
        } else {
            // unreachable
            throw new IllegalStateException("Unknown body type: " + parsedBody);
        }
    }

    public String headers(MessageHeaders headers) {
        StringJoiner ret = new StringJoiner("\n");
        for (var header : headers.map().entrySet()) {
            for (var value : header.getValue()) {
                ret.add(STR."\{header.getKey()}: \{value}");
            }
        }
        return ret.toString();
    }

    public void close() throws IOException {
        reader.close();
    }

    @Override
    public SerializableCrawlData next() throws IOException {
        if (!hasNext())
            throw new NoSuchElementException();
        try {
            return next;
        }
        finally {
            next = null;
        }
    }

}
