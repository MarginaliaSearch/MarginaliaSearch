package nu.marginalia.model.processed;

import nu.marginalia.slop.SlopTable;
import nu.marginalia.slop.column.array.ObjectArrayColumn;
import nu.marginalia.slop.column.primitive.IntColumn;
import nu.marginalia.slop.column.string.EnumColumn;
import nu.marginalia.slop.column.string.TxtStringColumn;
import nu.marginalia.slop.desc.StorageType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

    private static final TxtStringColumn domainsColumn = new TxtStringColumn("domain", StandardCharsets.UTF_8, StorageType.GZIP);
    private static final EnumColumn statesColumn = new EnumColumn("state", StandardCharsets.US_ASCII, StorageType.PLAIN);
    private static final TxtStringColumn redirectDomainsColumn = new TxtStringColumn("redirectDomain", StandardCharsets.UTF_8, StorageType.GZIP);
    private static final TxtStringColumn ipColumn = new TxtStringColumn("ip", StandardCharsets.US_ASCII, StorageType.GZIP);

    private static final IntColumn knownUrlsColumn = new IntColumn("knownUrls", StorageType.PLAIN);
    private static final IntColumn goodUrlsColumn = new IntColumn("goodUrls", StorageType.PLAIN);
    private static final IntColumn visitedUrlsColumn = new IntColumn("visitedUrls", StorageType.PLAIN);

    private static final ObjectArrayColumn<String> rssFeedsColumn = new TxtStringColumn("rssFeeds", StandardCharsets.UTF_8, StorageType.GZIP).asArray();


    public static class DomainNameReader extends SlopTable {
        private final TxtStringColumn.Reader domainsReader;

        public DomainNameReader(Path baseDir, int page) throws IOException {
            this(new Ref<>(baseDir, page));
        }

        public DomainNameReader(SlopTable.Ref<SlopDomainRecord> ref) throws IOException {
            super(ref);

            domainsReader = domainsColumn.open(this);
        }

        public boolean hasMore() throws IOException {
            return domainsReader.hasRemaining();
        }

        public String next() throws IOException {
            return domainsReader.get();
        }
    }

    public static class DomainWithIpReader extends SlopTable {
        private final TxtStringColumn.Reader domainsReader;
        private final TxtStringColumn.Reader ipReader;

        public DomainWithIpReader(SlopTable.Ref<SlopDomainRecord> ref) throws IOException {
            super(ref);

            domainsReader = domainsColumn.open(this);
            ipReader = ipColumn.open(this);
        }

        public DomainWithIpReader(Path baseDir, int page) throws IOException {
            this(new Ref<>(baseDir, page));
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

    public static class Reader extends SlopTable {
        private final TxtStringColumn.Reader domainsReader;
        private final EnumColumn.Reader statesReader;
        private final TxtStringColumn.Reader redirectReader;
        private final TxtStringColumn.Reader ipReader;

        private final IntColumn.Reader knownUrlsReader;
        private final IntColumn.Reader goodUrlsReader;
        private final IntColumn.Reader visitedUrlsReader;

        private final ObjectArrayColumn<String>.Reader rssFeedsReader;

        public Reader(SlopTable.Ref<SlopDomainRecord> ref) throws IOException {
            super(ref);

            domainsReader = domainsColumn.open(this);
            statesReader = statesColumn.open(this);
            redirectReader = redirectDomainsColumn.open(this);
            ipReader = ipColumn.open(this);

            knownUrlsReader = knownUrlsColumn.open(this);
            goodUrlsReader = goodUrlsColumn.open(this);
            visitedUrlsReader = visitedUrlsColumn.open(this);

            rssFeedsReader = rssFeedsColumn.open(this);
        }

        public Reader(Path baseDir, int page) throws IOException {
            this(new Ref<>(baseDir, page));
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
            return new SlopDomainRecord(
                    domainsReader.get(),
                    knownUrlsReader.get(),
                    goodUrlsReader.get(),
                    visitedUrlsReader.get(),
                    statesReader.get(),
                    redirectReader.get(),
                    ipReader.get(),
                    rssFeedsReader.get()
            );
        }
    }

    public static class Writer extends SlopTable {
        private final TxtStringColumn.Writer domainsWriter;
        private final EnumColumn.Writer statesWriter;
        private final TxtStringColumn.Writer redirectWriter;
        private final TxtStringColumn.Writer ipWriter;

        private final IntColumn.Writer knownUrlsWriter;
        private final IntColumn.Writer goodUrlsWriter;
        private final IntColumn.Writer visitedUrlsWriter;

        private final ObjectArrayColumn<String>.Writer rssFeedsWriter;

        public Writer(Path baseDir, int page) throws IOException {
            super(baseDir, page);

            domainsWriter = domainsColumn.create(this);
            statesWriter = statesColumn.create(this);
            redirectWriter = redirectDomainsColumn.create(this);
            ipWriter = ipColumn.create(this);

            knownUrlsWriter = knownUrlsColumn.create(this);
            goodUrlsWriter = goodUrlsColumn.create(this);
            visitedUrlsWriter = visitedUrlsColumn.create(this);

            rssFeedsWriter = rssFeedsColumn.create(this);
        }

        public void write(SlopDomainRecord record) throws IOException {
            domainsWriter.put(record.domain());
            statesWriter.put(record.state());
            redirectWriter.put(record.redirectDomain());
            ipWriter.put(record.ip());

            knownUrlsWriter.put(record.knownUrls());
            goodUrlsWriter.put(record.goodUrls());
            visitedUrlsWriter.put(record.visitedUrls());

            rssFeedsWriter.put(record.rssFeeds());
        }
    }
}
