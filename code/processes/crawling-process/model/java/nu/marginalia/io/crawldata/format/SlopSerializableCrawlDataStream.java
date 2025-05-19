package nu.marginalia.io.crawldata.format;

import nu.marginalia.contenttype.ContentType;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.*;
import nu.marginalia.slop.SlopCrawlDataRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.NoSuchElementException;

public class SlopSerializableCrawlDataStream implements AutoCloseable, SerializableCrawlDataStream {
    private static final Logger logger = LoggerFactory.getLogger(SlopSerializableCrawlDataStream.class);

    private final SlopCrawlDataRecord.FilteringReader reader;

    // Holds the next value.  This is not a buffer, but to deal with the fact that
    // we sometimes generate multiple SerializableCrawlData records for a single input
    private final Deque<SerializableCrawlData> nextQ = new ArrayDeque<>();

    private boolean wroteDomainRecord = false;
    private final Path path;

    public SlopSerializableCrawlDataStream(Path file) throws IOException {
        path = file;
        reader = new SlopCrawlDataRecord.FilteringReader(file) {
            @Override
            public boolean filter(String url, int status, String contentType) {
                String ctLc = contentType.toLowerCase();

                // Permit all plain text content types
                if (ctLc.startsWith("text/"))
                    return true;
                // PDF
                else if (ctLc.startsWith("application/pdf"))
                    return true;
                else if (ctLc.startsWith("x-marginalia/"))
                    return true;

                return false;
            }
        };
    }

    @Override
    public Path path() {
        return path;
    }

    public static int sizeHint(Path path) {
        // Only calculate size hint for large files
        // (the reason we calculate them in the first place is to assess whether it is large
        // because it has many documents, or because it is a small number of large documents)
        try {
            if (Files.size(path) > 10_000_000) {
                return SlopCrawlDataRecord.countGoodStatusCodes(path);
            }
        } catch (IOException e) {
            // suppressed
        }

        return 0;
    }

    @Override
    public boolean hasNext() {
        try {
            while (reader.hasRemaining() && nextQ.isEmpty()) {
                try {
                    var nextRecord = reader.get();
                    if (!wroteDomainRecord) {
                        createDomainRecord(nextRecord);
                        wroteDomainRecord = true;
                    }

                    createDocumentRecord(nextRecord);
                } catch (Exception ex) {
                    logger.error("Failed to create document record", ex);
                }
            }
            return !nextQ.isEmpty();
        }
        catch (IOException ex) {
            return false;
        }
    }

    private void createDomainRecord(SlopCrawlDataRecord parquetRecord) throws URISyntaxException {

        CrawlerDomainStatus status = CrawlerDomainStatus.OK;
        String statusReason = "";

        String redirectDomain = null;

        // The advisory content types are used to signal various states of the crawl
        // that are not actual crawled documents.

        switch (parquetRecord.contentType()) {
            case "x-marginalia/advisory;state=redirect" -> {
                EdgeUrl crawledUrl = new EdgeUrl(parquetRecord.url());
                redirectDomain = crawledUrl.getDomain().toString();
                status = CrawlerDomainStatus.REDIRECT;
            }
            case "x-marginalia/advisory;state=blocked" -> {
                status = CrawlerDomainStatus.BLOCKED;
            }
            case "x-marginalia/advisory;state=error" -> {
                status = CrawlerDomainStatus.ERROR;
                statusReason = new String(parquetRecord.body());
            }
        }

        nextQ.add(new CrawledDomain(
                parquetRecord.domain(),
                redirectDomain,
                status.toString(),
                statusReason,
                parquetRecord.ip(),
                new ArrayList<>(),
                new ArrayList<>()
        ));
    }

    private void createDocumentRecord(SlopCrawlDataRecord nextRecord) {
        CrawlerDocumentStatus status = CrawlerDocumentStatus.OK;

        if (nextRecord.contentType().startsWith("x-marginalia/advisory;state=content-type-failed-probe")) {
            status = CrawlerDocumentStatus.BAD_CONTENT_TYPE;
        }
        else if (nextRecord.contentType().startsWith("x-marginalia/advisory;state=robots-txt-skipped")) {
            status = CrawlerDocumentStatus.ROBOTS_TXT;
        }
        else if (nextRecord.contentType().startsWith("x-marginalia/advisory")) {
            // we don't care about the other advisory content types here
            return;
        }
        else if (nextRecord.body() != null) {
            try {
                ContentType.parse(nextRecord.contentType());
            } catch (Exception ex) {
                logger.error("Failed to convert body to string", ex);
                status = CrawlerDocumentStatus.BAD_CHARSET;
            }
        }
        else {
            status = CrawlerDocumentStatus.ERROR;
        }

        nextQ.add(new CrawledDocument("",
                nextRecord.url(),
                nextRecord.contentType(),
                Instant.ofEpochMilli(nextRecord.timestamp()).toString(),
                nextRecord.httpStatus(),
                status.toString(),
                "",
                nextRecord.headers(),
                nextRecord.body(),
                // this field isn't actually used, maybe we can skip calculating it?
                nextRecord.cookies(),
                nextRecord.requestTimeMs(),
                null,
                null));
    }

    public void close() throws IOException {
        reader.close();
    }

    @Override
    public SerializableCrawlData next() throws IOException {
        if (!hasNext())
            throw new NoSuchElementException();

        return nextQ.poll();
    }

}
