package nu.marginalia.crawling.retreival;

import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.HttpFetcher;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.crawling.model.SerializableCrawlData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Tag("slow")
class CrawlerRetreiverTest {

    @Test
    public void testEmptySet() throws IOException {
        // Tests the case when there are no URLs provided in the crawl set and the
        // crawler needs to guess the protocol

        var specs = new CrawlingSpecification("1", 5, "www.marginalia.nu", new ArrayList<>());

        HttpFetcher fetcher = new HttpFetcher("test.marginalia.nu");


        List<SerializableCrawlData> data = new ArrayList<>();

        new CrawlerRetreiver(fetcher, specs, data::add).fetch();

        Assertions.assertTrue(
                data.stream().filter(CrawledDocument.class::isInstance)
                    .map(CrawledDocument.class::cast)
                    .filter(doc -> "OK".equals(doc.crawlerStatus))
                    .count() > 1
        );
    }

}