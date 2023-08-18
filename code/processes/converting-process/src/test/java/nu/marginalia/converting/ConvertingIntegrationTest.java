package nu.marginalia.converting;


import com.google.inject.Guice;
import com.google.inject.Injector;
import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.crawl.UrlIndexingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConvertingIntegrationTest {

    private DomainProcessor domainProcessor;

    @BeforeEach
    public void setUp() {
        Injector injector = Guice.createInjector(
                new ConvertingIntegrationTestModule()
        );

        domainProcessor = injector.getInstance(DomainProcessor.class);
    }

    @Test
    public void testEmptyDomain() {
        var docs = new ArrayList<CrawledDocument>();

        var domain = new CrawledDomain("123", "memex.marginalia.nu", null, "OK", "-", "127.0.0.1",
                docs, Collections.emptyList());
        var ret = domainProcessor.process(asSerializableCrawlData(domain));

        assertEquals(ret.state, DomainIndexingState.ACTIVE);
        assertEquals(ret.domain, new EdgeDomain("memex.marginalia.nu"));
        assertTrue(ret.documents.isEmpty());
    }
    @Test
    public void testMemexMarginaliaNuDateInternalConsistency() throws IOException {
        var ret = domainProcessor.process(asSerializableCrawlData(readMarginaliaWorkingSet()));
        ret.documents.stream().filter(ProcessedDocument::isProcessedFully).forEach(doc -> {
            int year = PubDate.fromYearByte(doc.details.metadata.year());
            Integer yearMeta = doc.details.pubYear;
            if (yearMeta != null) {
                assertEquals(year, (int) yearMeta, doc.url.toString());
            }

        });
    }

    @Test
    public void testMemexMarginaliaNu() throws IOException {
        var ret = domainProcessor.process(asSerializableCrawlData(readMarginaliaWorkingSet()));
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
            assertEquals(HtmlStandard.HTML5, details.standard);

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
                    readClassPathFile(p.toString()),
                    Double.toString(Math.random()),
                    "https://memex.marginalia.nu/" + file,
                    null,
                    ""
                    );
            docs.add(doc);
        }

        return new CrawledDomain(
                "1",
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
        if (domain.doc != null) {
            data.addAll(domain.doc);
        }
        data.add(domain);

        return SerializableCrawlDataStream.fromIterator(data.iterator());
    }

}
