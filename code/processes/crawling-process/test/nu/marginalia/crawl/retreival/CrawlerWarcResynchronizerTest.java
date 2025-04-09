package nu.marginalia.crawl.retreival;

import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRequest;
import org.netpreserve.jwarc.WarcResponse;

import java.io.IOException;
import java.net.URISyntaxException;
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
        httpClient = HttpClients.createDefault();

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
        try (var oldRecorder = new WarcRecorder(fileName, new BasicCookieStore())) {
            fetchUrl(oldRecorder, "https://www.marginalia.nu/");
            fetchUrl(oldRecorder, "https://www.marginalia.nu/log/");
            fetchUrl(oldRecorder, "https://www.marginalia.nu/feed/");
        } catch (Exception e) {
            fail(e);
        }

        var crawlFrontier = new DomainCrawlFrontier(new EdgeDomain("www.marginalia.nu"), List.of(), 100);

        try (var newRecorder = new WarcRecorder(outputFile, new BasicCookieStore())) {
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
        var req = ClassicRequestBuilder.get(new java.net.URI(url))
                .addHeader("User-agent", "test.marginalia.nu")
                .addHeader("Accept-Encoding", "gzip")
                .build();
        recorder.fetch(httpClient, req);
    }
}