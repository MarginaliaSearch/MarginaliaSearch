package nu.marginalia.model.processed;

import nu.marginalia.slop.column.primitive.IntColumnReader;
import nu.marginalia.slop.column.primitive.IntColumnWriter;
import nu.marginalia.slop.column.string.StringColumnReader;
import nu.marginalia.slop.column.string.StringColumnWriter;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record SlopDomainRecord(
        String domain,
        int knownUrls,
        int goodUrls,
        int visitedUrls,
        String state,
        String redirectDomain,
        String ip,
        List<String> rssFeeds)
{

    public record DomainWithIpProjection(
            String domain,
            String ip)
    {}

    private static final ColumnDesc<StringColumnReader, StringColumnWriter> domainsColumn = new ColumnDesc<>("domain", ColumnType.TXTSTRING, StorageType.GZIP);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> statesColumn = new ColumnDesc<>("state", ColumnType.ENUM_LE, StorageType.PLAIN);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> redirectDomainsColumn = new ColumnDesc<>("redirectDomain", ColumnType.TXTSTRING, StorageType.GZIP);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> ipColumn = new ColumnDesc<>("ip", ColumnType.TXTSTRING, StorageType.GZIP);

    private static final ColumnDesc<IntColumnReader, IntColumnWriter> knownUrlsColumn = new ColumnDesc<>("knownUrls", ColumnType.INT_LE, StorageType.PLAIN);
    private static final ColumnDesc<IntColumnReader, IntColumnWriter> goodUrlsColumn = new ColumnDesc<>("goodUrls", ColumnType.INT_LE, StorageType.PLAIN);
    private static final ColumnDesc<IntColumnReader, IntColumnWriter> visitedUrlsColumn = new ColumnDesc<>("visitedUrls", ColumnType.INT_LE, StorageType.PLAIN);

    private static final ColumnDesc<IntColumnReader, IntColumnWriter> rssFeedsCountColumn = new ColumnDesc<>("rssFeeds", ColumnType.INT_LE, StorageType.GZIP);
    private static final ColumnDesc<StringColumnReader, StringColumnWriter> rssFeedsColumn = new ColumnDesc<>("rssFeeds", ColumnType.TXTSTRING, StorageType.GZIP);


    public static class DomainNameReader implements AutoCloseable {
        private final StringColumnReader domainsReader;

        public DomainNameReader(SlopPageRef<SlopDomainRecord> page) throws IOException {
            this(page.baseDir(), page.page());
        }

        public DomainNameReader(Path baseDir, int page) throws IOException {
            domainsReader = domainsColumn.forPage(page).open(baseDir);
        }


        @Override
        public void close() throws IOException {
            domainsReader.close();
        }

        public boolean hasMore() throws IOException {
            return domainsReader.hasRemaining();
        }

        public String next() throws IOException {
            return domainsReader.get();
        }
    }

    public static class DomainWithIpReader implements AutoCloseable {
        private final StringColumnReader domainsReader;
        private final StringColumnReader ipReader;

        public DomainWithIpReader(SlopPageRef<SlopDomainRecord> page) throws IOException {
            this(page.baseDir(), page.page());
        }

        public DomainWithIpReader(Path baseDir, int page) throws IOException {
            domainsReader = domainsColumn.forPage(page).open(baseDir);
            ipReader = ipColumn.forPage(page).open(baseDir);
        }


        @Override
        public void close() throws IOException {
            domainsReader.close();
            ipReader.close();
        }

        public boolean hasMore() throws IOException {
            return domainsReader.hasRemaining();
        }

        public DomainWithIpProjection next() throws IOException {

            return new DomainWithIpProjection(
                    domainsReader.get(),
                    ipReader.get()
            );
        }
    }

    public static class Reader implements AutoCloseable {
        private final StringColumnReader domainsReader;
        private final StringColumnReader statesReader;
        private final StringColumnReader redirectReader;
        private final StringColumnReader ipReader;

        private final IntColumnReader knownUrlsReader;
        private final IntColumnReader goodUrlsReader;
        private final IntColumnReader visitedUrlsReader;

        private final IntColumnReader rssFeedsCountReader;
        private final StringColumnReader rssFeedsReader;

        public Reader(SlopPageRef<SlopDomainRecord> page) throws IOException {
            this(page.baseDir(), page.page());
        }

        public Reader(Path baseDir, int page) throws IOException {
            domainsReader = domainsColumn.forPage(page).open(baseDir);
            statesReader = statesColumn.forPage(page).open(baseDir);
            redirectReader = redirectDomainsColumn.forPage(page).open(baseDir);
            ipReader = ipColumn.forPage(page).open(baseDir);

            knownUrlsReader = knownUrlsColumn.forPage(page).open(baseDir);
            goodUrlsReader = goodUrlsColumn.forPage(page).open(baseDir);
            visitedUrlsReader = visitedUrlsColumn.forPage(page).open(baseDir);

            rssFeedsCountReader = rssFeedsCountColumn.forPage(page).open(baseDir);
            rssFeedsReader = rssFeedsColumn.forPage(page).open(baseDir);
        }


        @Override
        public void close() throws IOException {
            domainsReader.close();
            statesReader.close();
            redirectReader.close();
            ipReader.close();

            knownUrlsReader.close();
            goodUrlsReader.close();
            visitedUrlsReader.close();

            rssFeedsCountReader.close();
            rssFeedsReader.close();
        }

        public boolean hasMore() throws IOException {
            return domainsReader.hasRemaining();
        }

        public void forEach(Consumer<SlopDomainRecord> recordConsumer) throws IOException {
            while (hasMore()) {
                recordConsumer.accept(next());
            }
        }

        public SlopDomainRecord next() throws IOException {
            List<String> rssFeeds = new ArrayList<>();
            int rssFeedsCount = rssFeedsCountReader.get();
            for (int i = 0; i < rssFeedsCount; i++) {
                rssFeeds.add(rssFeedsReader.get());
            }

            return new SlopDomainRecord(
                    domainsReader.get(),
                    knownUrlsReader.get(),
                    goodUrlsReader.get(),
                    visitedUrlsReader.get(),
                    statesReader.get(),
                    redirectReader.get(),
                    ipReader.get(),
                    rssFeeds
            );
        }
    }

    public static class Writer implements AutoCloseable {
        private final StringColumnWriter domainsWriter;
        private final StringColumnWriter statesWriter;
        private final StringColumnWriter redirectWriter;
        private final StringColumnWriter ipWriter;

        private final IntColumnWriter knownUrlsWriter;
        private final IntColumnWriter goodUrlsWriter;
        private final IntColumnWriter visitedUrlsWriter;

        private final IntColumnWriter rssFeedsCountWriter;
        private final StringColumnWriter rssFeedsWriter;

        public Writer(Path baseDir, int page) throws IOException {
            domainsWriter = domainsColumn.forPage(page).create(baseDir);
            statesWriter = statesColumn.forPage(page).create(baseDir);
            redirectWriter = redirectDomainsColumn.forPage(page).create(baseDir);
            ipWriter = ipColumn.forPage(page).create(baseDir);

            knownUrlsWriter = knownUrlsColumn.forPage(page).create(baseDir);
            goodUrlsWriter = goodUrlsColumn.forPage(page).create(baseDir);
            visitedUrlsWriter = visitedUrlsColumn.forPage(page).create(baseDir);

            rssFeedsCountWriter = rssFeedsCountColumn.forPage(page).create(baseDir);
            rssFeedsWriter = rssFeedsColumn.forPage(page).create(baseDir);
        }

        public void write(SlopDomainRecord record) throws IOException {
            domainsWriter.put(record.domain());
            statesWriter.put(record.state());
            redirectWriter.put(record.redirectDomain());
            ipWriter.put(record.ip());

            knownUrlsWriter.put(record.knownUrls());
            goodUrlsWriter.put(record.goodUrls());
            visitedUrlsWriter.put(record.visitedUrls());

            rssFeedsCountWriter.put(record.rssFeeds().size());
            for (String rssFeed : record.rssFeeds()) {
                rssFeedsWriter.put(rssFeed);
            }
        }
        
        @Override
        public void close() throws IOException {
            domainsWriter.close();
            statesWriter.close();
            redirectWriter.close();
            ipWriter.close();

            knownUrlsWriter.close();
            goodUrlsWriter.close();
            visitedUrlsWriter.close();

            rssFeedsCountWriter.close();
            rssFeedsWriter.close();
        }
    }
}
