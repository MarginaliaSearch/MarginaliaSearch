package nu.marginalia.io.crawldata.format;

import lombok.SneakyThrows;
import nu.marginalia.contenttype.ContentType;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.*;
import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecord;
import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecordFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ParquetSerializableCrawlDataStream implements AutoCloseable, SerializableCrawlDataStream {
    private static final Logger logger = LoggerFactory.getLogger(ParquetSerializableCrawlDataStream.class);

    private final MurmurHash3_128 hash = new MurmurHash3_128();
    private final Iterator<CrawledDocumentParquetRecord> backingIterator;
    private final Deque<SerializableCrawlData> nextQ = new ArrayDeque<>();
    private boolean wroteDomainRecord = false;
    private final Path path;

    public ParquetSerializableCrawlDataStream(Path file) throws IOException {
        path = file;
        backingIterator = CrawledDocumentParquetRecordFileReader.stream(file).iterator();
    }

    @Override
    public Path path() {
        return path;
    }

    public int sizeHint() {
        // Only calculate size hint for large files
        // (the reason we calculate them in the first place is to assess whether it is large
        // because it has many documents, or because it is a small number of large documents)
        try {
            if (Files.size(path) > 10_000_000) {
                return CrawledDocumentParquetRecordFileReader.countGoodStatusCodes(path);
            }
        } catch (IOException e) {
            // suppressed
        }

        return 0;
    }

    @Override
    @SneakyThrows
    public boolean hasNext() {
        while (backingIterator.hasNext() && nextQ.isEmpty()) {
            var nextRecord = backingIterator.next();
            if (!wroteDomainRecord) {
                createDomainRecord(nextRecord);
                wroteDomainRecord = true;
            }

            try {
                createDocumentRecord(nextRecord);
            }
            catch (Exception ex) {
                logger.error("Failed to create document record", ex);
            }
        }
        return !nextQ.isEmpty();
    }

    private void createDomainRecord(CrawledDocumentParquetRecord parquetRecord) throws URISyntaxException {

        CrawlerDomainStatus status = CrawlerDomainStatus.OK;
        String statusReason = "";

        String redirectDomain = null;

        // The advisory content types are used to signal various states of the crawl
        // that are not actual crawled documents.

        if (parquetRecord.contentType.equals("x-marginalia/advisory;state=redirect")) {
            EdgeUrl crawledUrl = new EdgeUrl(parquetRecord.url);
            redirectDomain = crawledUrl.getDomain().toString();
            status = CrawlerDomainStatus.REDIRECT;
        }
        else if (parquetRecord.contentType.equals("x-marginalia/advisory;state=blocked")) {
            status = CrawlerDomainStatus.BLOCKED;
        }
        else if (parquetRecord.contentType.equals("x-marginalia/advisory;state=error")) {
            status = CrawlerDomainStatus.ERROR;
            statusReason = new String(parquetRecord.body);
        }

        nextQ.add(new CrawledDomain(
                parquetRecord.domain,
                redirectDomain,
                status.toString(),
                statusReason,
                parquetRecord.ip,
                new ArrayList<>(),
                new ArrayList<>()
        ));
    }

    private void createDocumentRecord(CrawledDocumentParquetRecord nextRecord) {
        String bodyString = "";
        CrawlerDocumentStatus status = CrawlerDocumentStatus.OK;

        if (nextRecord.contentType.startsWith("x-marginalia/advisory;state=content-type-failed-probe")) {
            status = CrawlerDocumentStatus.BAD_CONTENT_TYPE;
        }
        else if (nextRecord.contentType.startsWith("x-marginalia/advisory;state=robots-txt-skipped")) {
            status = CrawlerDocumentStatus.ROBOTS_TXT;
        }
        else if (nextRecord.contentType.startsWith("x-marginalia/advisory")) {
            // we don't care about the other advisory content types here
            return;
        }
        else if (nextRecord.body != null) {
            try {
                bodyString = DocumentBodyToString.getStringData(
                        ContentType.parse(nextRecord.contentType),
                        nextRecord.body);
            } catch (Exception ex) {
                logger.error("Failed to convert body to string", ex);
                status = CrawlerDocumentStatus.BAD_CHARSET;
            }
        }
        else {
            status = CrawlerDocumentStatus.ERROR;
        }

        String etag = nextRecord.etagHeader;
        String lastModified = nextRecord.lastModifiedHeader;

        nextQ.add(new CrawledDocument("",
                nextRecord.url,
                nextRecord.contentType,
                nextRecord.timestamp.toString(),
                nextRecord.httpStatus,
                status.toString(),
                "",
                nextRecord.headers,
                bodyString,
                Long.toHexString(hash.hashNearlyASCII(bodyString)), // this field isn't actually used, maybe we can skip calculating it?
                nextRecord.url,
                null,
                "",
                nextRecord.cookies,
                lastModified,
                etag));
    }

    public void close() throws IOException {}

    @Override
    public SerializableCrawlData next() throws IOException {
        if (!hasNext())
            throw new NoSuchElementException();

        return nextQ.poll();
    }

}
