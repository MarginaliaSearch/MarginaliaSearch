package nu.marginalia.livecrawler;

import nu.marginalia.db.DomainBlacklistImpl;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawledDomain;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

class SimpleLinkScraperTest {
    private Path tempDir;
    private LiveCrawlDataSet dataSet;

    @BeforeEach
    public void setUp() throws IOException, SQLException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
        dataSet = new LiveCrawlDataSet(tempDir);
    }


    @AfterEach
    public void tearDown() throws Exception {
        dataSet.close();
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    @Test
    public void testRetrieveNow() throws Exception {
        var scraper = new SimpleLinkScraper(dataSet, null, Mockito.mock(DomainBlacklistImpl.class));
        scraper.retrieveNow(new EdgeDomain("www.marginalia.nu"), 1, List.of("https://www.marginalia.nu/"));

        var streams = dataSet.getDataStreams();
        Assertions.assertEquals(1, streams.size());

        SerializableCrawlDataStream firstStream = streams.iterator().next();
        Assertions.assertTrue(firstStream.hasNext());

        if (firstStream.next() instanceof CrawledDomain domain) {
            Assertions.assertEquals("www.marginalia.nu",domain.getDomain());
        }
        else {
            Assertions.fail();
        }

        Assertions.assertTrue(firstStream.hasNext());

        if ((firstStream.next() instanceof CrawledDocument document)) {
            // verify we decompress the body string
            Assertions.assertTrue(document.documentBody.startsWith("<!doctype"));
        }
        else{
            Assertions.fail();
        }

        Assertions.assertFalse(firstStream.hasNext());
    }
}