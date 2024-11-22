package nu.marginalia.livecrawler;

import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawledDomain;
import nu.marginalia.model.crawldata.SerializableCrawlData;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LiveCrawlDataSetTest {

    @Test
    public void testGetDataSet() throws Exception {
        Path tempDir = Files.createTempDirectory("live-crawl-data-set-test");
        try (LiveCrawlDataSet dataSet = new LiveCrawlDataSet(tempDir)) {

            Assertions.assertFalse(dataSet.hasUrl("https://www.example.com/"));
            dataSet.saveDocument(
                    1,
                    new EdgeUrl("https://www.example.com/"),
                    "test",
                    "test",
                    "test"
            );
            Assertions.assertTrue(dataSet.hasUrl("https://www.example.com/"));

            var streams = dataSet.getDataStreams();
            Assertions.assertEquals(1, streams.size());
            var stream = streams.iterator().next();

            List<SerializableCrawlData> data = new ArrayList<>();
            while (stream.hasNext()) {
                data.add(stream.next());
            }

            int dataCount = 0;
            int domainCount = 0;

            for (var item : data) {
                switch (item) {
                    case CrawledDomain domain -> {
                        domainCount++;
                        Assertions.assertEquals("www.example.com", domain.getDomain());
                    }
                    case CrawledDocument document -> {
                        dataCount++;
                        Assertions.assertEquals("https://www.example.com/", document.url);
                        Assertions.assertEquals("test", document.documentBody);
                    }
                }
            }

            Assertions.assertEquals(1, dataCount);
            Assertions.assertEquals(1, domainCount);
        }
        finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    @Test
    public void testHasUrl() throws Exception {
        Path tempDir = Files.createTempDirectory("live-crawl-data-set-test");
        try (LiveCrawlDataSet dataSet = new LiveCrawlDataSet(tempDir)) {
            Assertions.assertFalse(dataSet.hasUrl("https://www.example.com/"));
            dataSet.saveDocument(
                    1,
                    new EdgeUrl("https://www.example.com/saved"),
                    "test",
                    "test",
                    "test"
            );
            Assertions.assertTrue(dataSet.hasUrl("https://www.example.com/saved"));

            dataSet.flagAsBad(new EdgeUrl("https://www.example.com/bad"));

            Assertions.assertTrue(dataSet.hasUrl("https://www.example.com/bad"));

            Assertions.assertFalse(dataSet.hasUrl("https://www.example.com/notPresent"));
        }
        finally {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

}