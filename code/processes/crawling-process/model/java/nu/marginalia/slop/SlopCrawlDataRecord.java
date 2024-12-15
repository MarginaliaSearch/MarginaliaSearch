package nu.marginalia.slop;

import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecord;
import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecordFileReader;
import nu.marginalia.slop.column.array.ByteArrayColumn;
import nu.marginalia.slop.column.primitive.ByteColumn;
import nu.marginalia.slop.column.primitive.LongColumn;
import nu.marginalia.slop.column.primitive.ShortColumn;
import nu.marginalia.slop.column.string.EnumColumn;
import nu.marginalia.slop.column.string.StringColumn;
import nu.marginalia.slop.desc.StorageType;
import nu.marginalia.slop.storage.LargeItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public record SlopCrawlDataRecord(String domain,
                                  String url,
                                  String ip,
                                  boolean cookies,
                                  int httpStatus,
                                  long timestamp,
                                  String contentType,
                                  byte[] body,
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
    private static final StringColumn headerColumn = new StringColumn("header", StandardCharsets.UTF_8, StorageType.ZSTD);

    public SlopCrawlDataRecord(CrawledDocumentParquetRecord parquetRecord) {
        this(parquetRecord.domain,
                parquetRecord.url,
                parquetRecord.ip,
                parquetRecord.cookies,
                parquetRecord.httpStatus,
                parquetRecord.timestamp.toEpochMilli(),
                parquetRecord.contentType,
                parquetRecord.body,
                parquetRecord.headers
                );
    }

    public static void convertFromParquet(Path parquetInput, Path slopOutput) throws IOException {
        try (var writer = new Writer(slopOutput)) {
            CrawledDocumentParquetRecordFileReader.stream(parquetInput).forEach(
                    parquetRecord -> {
                        try {
                            writer.write(new SlopCrawlDataRecord(parquetRecord));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

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
            headerColumnWriter.put(record.headers);
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
                String contentType = contentTypeColumnReader.get();

                LargeItem<byte[]> body = bodyColumnReader.getLarge();
                LargeItem<String> headers = headerColumnReader.getLarge();

                if (filter(url, status, contentType)) {
                    next = new SlopCrawlDataRecord(
                            domain, url, ip, cookies, status, timestamp, contentType, body.get(), headers.get()
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
