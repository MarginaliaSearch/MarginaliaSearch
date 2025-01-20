package nu.marginalia.crawl.retreival;

import nu.marginalia.crawl.fetcher.Cookies;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRequest;
import org.netpreserve.jwarc.WarcResponse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CrawlerWarcResynchronizerTest {
    Path fileName;
    Path outputFile;
    HttpClient httpClient;
    @BeforeEach
    public void setUp() throws Exception {
        httpClient = HttpClient.newBuilder()
                .build();

        fileName = Files.createTempFile("test", ".warc.gz");
        outputFile = Files.createTempFile("test", ".warc.gz");
    }

    @AfterEach
    public void tearDown() throws Exception {
        Files.deleteIfExists(fileName);
        Files.deleteIfExists(outputFile);
    }

    @Test
    void run() throws IOException, URISyntaxException {
        try (var oldRecorder = new WarcRecorder(fileName, new Cookies())) {
            fetchUrl(oldRecorder, "https://www.marginalia.nu/");
            fetchUrl(oldRecorder, "https://www.marginalia.nu/log/");
            fetchUrl(oldRecorder, "https://www.marginalia.nu/feed/");
        } catch (Exception e) {
            fail(e);
        }

        var crawlFrontier = new DomainCrawlFrontier(new EdgeDomain("www.marginalia.nu"), List.of(), 100);

        try (var newRecorder = new WarcRecorder(outputFile, new Cookies())) {
            new CrawlerWarcResynchronizer(crawlFrontier, newRecorder).run(fileName);
        }

        assertTrue(crawlFrontier.isVisited(new EdgeUrl("https://www.marginalia.nu/")));
        assertTrue(crawlFrontier.isVisited(new EdgeUrl("https://www.marginalia.nu/log/")));
        assertTrue(crawlFrontier.isVisited(new EdgeUrl("https://www.marginalia.nu/feed/")));

        try (var warcReader = new WarcReader(outputFile)) {
            for (var item : warcReader) {
                if (item instanceof WarcRequest req) {
                    System.out.println("req:" + req.target());
                }
                if (item instanceof WarcResponse rsp) {
                    System.out.println("req:" + rsp.target());
                }
            }
        }

        new GZIPInputStream(Files.newInputStream(outputFile)).transferTo(System.out);
    }

    void fetchUrl(WarcRecorder recorder, String url) throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        var req = HttpRequest.newBuilder()
                .uri(new java.net.URI(url))
                .header("User-agent", "test.marginalia.nu")
                .header("Accept-Encoding", "gzip")
                .GET().build();
        recorder.fetch(httpClient, req);
    }
}