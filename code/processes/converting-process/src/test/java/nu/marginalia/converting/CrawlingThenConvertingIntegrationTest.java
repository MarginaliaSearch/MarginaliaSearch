package nu.marginalia.converting;

import com.google.inject.Guice;
import com.google.inject.Injector;
import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/* This is mostly a debugging utility */
@Tag("slow")
public class CrawlingThenConvertingIntegrationTest {
    private DomainProcessor domainProcessor;
    private HttpFetcher httpFetcher;

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
    }

    @Test
    public void crawlThenProcess() {
        var specs = CrawlingSpecification.builder()
                .id("some-string")
                .domain("www.marginalia.nu")
                .crawlDepth(10)
                .urls(List.of()) // add specific URLs to crawl here
                .build();

        CrawledDomain domain = crawl(specs);

        var output = domainProcessor.process(domain);

        for (var doc : output.documents) {
            if (doc.isOk()) {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.details.title);
            }
            else {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.stateReason);
            }
        }

    }

    private CrawledDomain crawl(CrawlingSpecification specs) {
        List<SerializableCrawlData> data = new ArrayList<>();

        new CrawlerRetreiver(httpFetcher, specs, data::add).fetch();

        CrawledDomain domain = data.stream().filter(CrawledDomain.class::isInstance).map(CrawledDomain.class::cast).findFirst().get();
        data.stream().filter(CrawledDocument.class::isInstance).map(CrawledDocument.class::cast).forEach(domain.doc::add);
        return domain;
    }
}
