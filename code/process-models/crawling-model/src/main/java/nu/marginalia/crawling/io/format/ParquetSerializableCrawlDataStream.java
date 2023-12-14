package nu.marginalia.crawling.io.format;

import lombok.SneakyThrows;
import nu.marginalia.contenttype.ContentType;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.CrawlerDomainStatus;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.crawling.parquet.CrawledDocumentParquetRecord;
import nu.marginalia.crawling.parquet.CrawledDocumentParquetRecordFileReader;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

public class ParquetSerializableCrawlDataStream implements AutoCloseable, SerializableCrawlDataStream {
    private static final Logger logger = LoggerFactory.getLogger(ParquetSerializableCrawlDataStream.class);

    private final Iterator<CrawledDocumentParquetRecord> backingIterator;
    private Deque<SerializableCrawlData> nextQ = new ArrayDeque<>();
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

    @Override
    @SneakyThrows
    public boolean hasNext() {
        while (backingIterator.hasNext() && nextQ.isEmpty()) {
            var nextRecord = backingIterator.next();
            if (!wroteDomainRecord) {
                createDomainRecord(nextRecord);
                wroteDomainRecord = true;
            }
            createDocumentRecord(nextRecord);
        }
        return !nextQ.isEmpty();
    }

    private void createDomainRecord(CrawledDocumentParquetRecord parquetRecord) throws URISyntaxException {

        CrawlerDomainStatus status = CrawlerDomainStatus.OK;
        String statusReason = "";

        String redirectDomain = null;
        if (parquetRecord.contentType.equals("x-marginalia/advisory;state=redir")) {
            EdgeUrl crawledUrl = new EdgeUrl(parquetRecord.url);
            redirectDomain = crawledUrl.getDomain().toString();
            status = CrawlerDomainStatus.REDIRECT;
        }
        else if (parquetRecord.contentType.equals("x-marginalia/advisory;state=blocked")) {
            status = CrawlerDomainStatus.BLOCKED; // FIXME we don't write this yet
        }
        else if (parquetRecord.contentType.equals("x-marginalia/advisory;state=error")) {
            status = CrawlerDomainStatus.ERROR;
            statusReason = new String(parquetRecord.body);
        }

        // FIXME -- cookies
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
        String bodyString = DocumentBodyToString.getStringData(
                ContentType.parse(nextRecord.contentType),
                nextRecord.body);

        // FIXME -- a lot of these fields are not set properly!
        nextQ.add(new CrawledDocument("",
                nextRecord.url,
                nextRecord.contentType,
                "",
                nextRecord.httpStatus,
                "OK",
                "",
                "",
                bodyString,
                "",
                nextRecord.url,
                null,
                ""));
    }

    public void close() throws IOException {
    }

    @Override
    public SerializableCrawlData next() throws IOException {
        if (!hasNext())
            throw new NoSuchElementException();

        return nextQ.poll();
    }

}
