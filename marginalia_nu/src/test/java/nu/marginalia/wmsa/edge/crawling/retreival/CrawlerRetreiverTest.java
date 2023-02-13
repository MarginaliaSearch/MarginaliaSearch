package nu.marginalia.wmsa.edge.crawling.retreival;

import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawlingSpecification;
import nu.marginalia.wmsa.edge.crawling.model.SerializableCrawlData;
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

        var specs = new CrawlingSpecification("1", 5, "memex.marginalia.nu", new ArrayList<>());

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