package nu.marginalia.converting;


import com.google.inject.Guice;
import com.google.inject.Injector;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawledDomain;
import nu.marginalia.model.crawldata.SerializableCrawlData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
public class ConvertingIntegrationTest {

    private static DomainProcessor domainProcessor;

    @BeforeAll
    public static void setUp() {
        Injector injector = Guice.createInjector(
                new ConvertingIntegrationTestModule()
        );

        domainProcessor = injector.getInstance(DomainProcessor.class);
    }

    @Test
    public void testEmptyDomain() {
        var docs = new ArrayList<CrawledDocument>();

        var domain = new CrawledDomain("memex.marginalia.nu", null, "OK", "-", "127.0.0.1",
                docs, Collections.emptyList());
        var ret = domainProcessor.fullProcessing(asSerializableCrawlData(domain));

        assertEquals(ret.state, DomainIndexingState.ACTIVE);
        assertEquals(ret.domain, new EdgeDomain("memex.marginalia.nu"));
        assertTrue(ret.documents.isEmpty());
    }

    @Test
    public void testBuggyCase() throws IOException {

        // Test used to inspect processing of crawl data, change path below to use
        Path problemCase = Path.of("/home/vlofgren/TestEnv/index-1/storage/crawl-data__25-09-15T16_33_57.245/46/64/4664ef43-blog.fermi.chat.slop.zip");
        if (!Files.exists(problemCase))
            return;

        ProcessedDomain result = domainProcessor.fullProcessing(SerializableCrawlDataStream.openDataStream(problemCase));
        for (ProcessedDocument doc : result.documents) {
            System.out.println(doc.url);
            if (doc.details == null) continue;

            System.out.println(doc.details.features);
            System.out.println(HtmlFeature.encode(doc.details.features) & HtmlFeature.AFFILIATE_LINK.getFeatureBit());
        }
    }

    @Test
    public void testMemexMarginaliaNuDateInternalConsistency() throws IOException {
        var ret = domainProcessor.fullProcessing(asSerializableCrawlData(readMarginaliaWorkingSet()));
        ret.documents.stream().filter(ProcessedDocument::isProcessedFully).forEach(doc -> {
            int year = PubDate.fromYearByte(doc.details.metadata.year());
            Integer yearMeta = doc.details.pubYear;
            if (yearMeta != null) {
                assertEquals(year, (int) yearMeta, doc.url.toString());
            }

        });
    }

    @Test
    public void testMemexMarginaliaNuFullProcessing() throws IOException {
        var ret = domainProcessor.fullProcessing(asSerializableCrawlData(readMarginaliaWorkingSet()));
        assertNotNull(ret);
        assertEquals(ret.state, DomainIndexingState.ACTIVE);
        assertEquals(ret.domain, new EdgeDomain("memex.marginalia.nu"));

        assertFalse(ret.documents.isEmpty());

        Map<UrlIndexingState, Integer> resultsByStatusCount = new HashMap<>();

        ret.documents.forEach(doc -> {
            resultsByStatusCount.merge(doc.state, 1, Integer::sum);
        });

        assertTrue(resultsByStatusCount.get(UrlIndexingState.OK) > 25);

        for (var doc : ret.documents) {

            if (!doc.isProcessedFully()) {
                continue;
            }

            var details = doc.details;

            assertTrue(details.title.length() > 4);
            assertTrue(details.description.length() > 4);
            assertEquals(DocumentFormat.HTML5, details.format);

        }
    }

    @Test
    public void testMemexMarginaliaNuSideloadProcessing() throws IOException {
        var ret = domainProcessor.simpleProcessing(asSerializableCrawlData(readMarginaliaWorkingSet()), 100);
        assertNotNull(ret);
        assertEquals("memex.marginalia.nu", ret.id());

        var domain = ret.getDomain();
        assertEquals(domain.domain, new EdgeDomain("memex.marginalia.nu"));

        List<ProcessedDocument> docsAll = new ArrayList<>();
        Map<UrlIndexingState, Integer> resultsByStatusCount = new HashMap<>();
        ret.getDocumentsStream().forEachRemaining(docsAll::add);
        assertTrue(docsAll.size() > 25);

        docsAll.forEach(doc -> resultsByStatusCount.merge(doc.state, 1, Integer::sum));

        assertTrue(resultsByStatusCount.get(UrlIndexingState.OK) > 25);

        for (var doc : docsAll) {

            if (!doc.isProcessedFully()) {
                continue;
            }

            var details = doc.details;

            assertTrue(details.metadata.size() > 0);
            assertTrue(details.title.length() > 4);
            assertTrue(details.description.length() > 4);
            assertEquals(DocumentFormat.HTML5, details.format);
        }
    }

    private CrawledDomain readMarginaliaWorkingSet() throws IOException {
        String index = readClassPathFile("memex-marginalia/index");
        String[] files = index.split("\n");

        var docs = new ArrayList<CrawledDocument>();

        for (String file : files) {
            Path p = Path.of("memex-marginalia/").resolve(file);

            var doc = new CrawledDocument("1",
                    "https://memex.marginalia.nu/" + file,
                    "text/html",
                    LocalTime.now().toString(),
                    200,
                    "OK",
                    "",
                    "",
                    readClassPathFile(p.toString()).getBytes(),
                    false,
                    -1,
                    null,
                    null
                    );
            docs.add(doc);
        }

        return new CrawledDomain(
                "memex.marginalia.nu",
                null,
                "OK",
                "",
                "127.0.0.1",
                docs, Collections.emptyList());
    }

    private String readClassPathFile(String s) throws IOException {
        return new String(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(s)).readAllBytes());
    }


    private SerializableCrawlDataStream asSerializableCrawlData(CrawledDomain domain) {
        List<SerializableCrawlData> data = new ArrayList<>();

        data.add(domain);

        if (domain.doc != null) {
            data.addAll(domain.doc);
        }


        return SerializableCrawlDataStream.fromIterator(data.iterator());
    }

}
