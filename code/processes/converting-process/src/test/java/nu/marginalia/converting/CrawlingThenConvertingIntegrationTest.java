package nu.marginalia.converting;

import com.google.inject.Guice;
import com.google.inject.Injector;
import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.DomainProber;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.io.format.ParquetSerializableCrawlDataStream;
import nu.marginalia.crawling.io.format.WarcSerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.crawling.parquet.CrawledDocumentParquetRecord;
import nu.marginalia.crawling.parquet.CrawledDocumentParquetRecordFileWriter;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/* This is mostly a debugging utility */
@Tag("slow")
public class CrawlingThenConvertingIntegrationTest {
    private DomainProcessor domainProcessor;
    private HttpFetcher httpFetcher;

    private Path fileName;
    private Path fileName2;

    @SneakyThrows
    @BeforeAll
    public static void setUpAll() {
        // this must be done to avoid java inserting its own user agent for the sitemap requests
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());
    }

    @SneakyThrows
    @BeforeEach
    public void setUp() {
        Injector injector = Guice.createInjector(
                new ConvertingIntegrationTestModule()
        );

        domainProcessor = injector.getInstance(DomainProcessor.class);
        httpFetcher = new HttpFetcherImpl(WmsaHome.getUserAgent().uaString());
        this.fileName = Files.createTempFile("crawling-then-converting", ".warc.gz");
        this.fileName2 = Files.createTempFile("crawling-then-converting", ".warc.gz");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(fileName);
        Files.deleteIfExists(fileName2);
    }

    @Test
    public void crawlThenProcess() throws IOException {
        var specs = CrawlSpecRecord.builder()
                .domain("www.marginalia.nu")
                .crawlDepth(10)
                .urls(List.of()) // add specific URLs to crawl here
                .build();

        CrawledDomain domain = crawl(specs);

        List<SerializableCrawlData> data = new ArrayList<>();
        data.add(domain);
        data.addAll(domain.doc);

        var output = domainProcessor.process(SerializableCrawlDataStream.fromIterator(data.iterator()));

        for (var doc : output.documents) {
            if (doc.isOk()) {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.details.title);
            }
            else {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.stateReason);
            }
        }

    }

    private CrawledDomain crawl(CrawlSpecRecord specs) throws IOException {
        List<SerializableCrawlData> data = new ArrayList<>();

        try (var recorder = new WarcRecorder(fileName)) {
            new CrawlerRetreiver(httpFetcher, new DomainProber(d -> true), specs, recorder).fetch();
        }

        CrawledDocumentParquetRecordFileWriter.convertWarc(specs.domain, fileName, fileName2);

        try (var reader = new ParquetSerializableCrawlDataStream(fileName2)) {
            while (reader.hasNext()) {
                data.add(reader.next());
            }
        }

        CrawledDomain domain = data.stream()
                .filter(CrawledDomain.class::isInstance)
                .map(CrawledDomain.class::cast)
                .findFirst()
                .get();
        data.stream().filter(CrawledDocument.class::isInstance).map(CrawledDocument.class::cast).forEach(domain.doc::add);
        return domain;
    }
}
