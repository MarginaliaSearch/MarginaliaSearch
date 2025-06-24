package nu.marginalia.livecrawler;

import nu.marginalia.coordination.LocalDomainCoordinator;
import nu.marginalia.db.DomainBlacklistImpl;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.livecrawler.io.HttpClientProvider;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.CrawledDocument;
import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;

class SimpleLinkScraperTest {
    private Path tempDir;
    private LiveCrawlDataSet dataSet;
    private CloseableHttpClient httpClient;

    @BeforeEach
    public void setUp() throws IOException, SQLException, NoSuchAlgorithmException, KeyManagementException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
        dataSet = new LiveCrawlDataSet(tempDir);
        httpClient = HttpClientProvider.createClient();
    }


    @AfterEach
    public void tearDown() throws Exception {
        dataSet.close();
        httpClient.close(CloseMode.IMMEDIATE);
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    @Test
    public void testRetrieveNow() throws Exception {
        var scraper = new SimpleLinkScraper(dataSet, new LocalDomainCoordinator(),  null, httpClient, Mockito.mock(DomainBlacklistImpl.class));
        int fetched = scraper.retrieveNow(new EdgeDomain("www.marginalia.nu"), 1, List.of("https://www.marginalia.nu/"));
        Assertions.assertEquals(1, fetched);

        var streams = dataSet.getDataStreams();
        Assertions.assertEquals(1, streams.size());

        SerializableCrawlDataStream firstStream = streams.iterator().next();
        Assertions.assertTrue(firstStream.hasNext());

        List<CrawledDocument> documents = firstStream.docsAsList();
        Assertions.assertEquals(1, documents.size());
        Assertions.assertTrue(documents.getFirst().documentBody().startsWith("<!doctype"));
    }



    @Test
    public void testRetrieveNow_Redundant() throws Exception {
        dataSet.saveDocument(1, new EdgeUrl("https://www.marginalia.nu/"), "<html>", "", "127.0.0.1");
        var scraper = new SimpleLinkScraper(dataSet, new LocalDomainCoordinator(),null, httpClient, Mockito.mock(DomainBlacklistImpl.class));

        // If the requested URL is already in the dataSet, we retrieveNow should shortcircuit and not fetch anything
        int fetched = scraper.retrieveNow(new EdgeDomain("www.marginalia.nu"), 1, List.of("https://www.marginalia.nu/"));
        Assertions.assertEquals(0, fetched);
    }
}