package nu.marginalia.crawl.retreival.fetcher;

import nu.marginalia.UserAgent;
import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.fetcher.Cookies;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecordFileReader;
import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecordFileWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRequest;
import org.netpreserve.jwarc.WarcResponse;
import org.netpreserve.jwarc.WarcXResponseReference;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarcRecorderTest {
    Path fileNameWarc;
    Path fileNameParquet;
    WarcRecorder client;

    HttpClient httpClient;
    @BeforeEach
    public void setUp() throws Exception {
        httpClient = HttpClient.newBuilder().build();

        fileNameWarc = Files.createTempFile("test", ".warc");
        fileNameParquet = Files.createTempFile("test", ".parquet");

        client = new WarcRecorder(fileNameWarc, new Cookies());
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
        Files.delete(fileNameWarc);
    }

    @Test
    void fetch() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        client.fetch(httpClient,
                HttpRequest.newBuilder()
                        .uri(new java.net.URI("https://www.marginalia.nu/"))
                        .header("User-agent", "test.marginalia.nu")
                        .header("Accept-Encoding", "gzip")
                        .GET().build()
        );

        Map<String, String> sampleData = new HashMap<>();
        try (var warcReader = new WarcReader(fileNameWarc)) {
            warcReader.forEach(record -> {
                if (record instanceof WarcRequest req) {
                    sampleData.put(record.type(), req.target());
                }
                if (record instanceof WarcResponse rsp) {
                    sampleData.put(record.type(), rsp.target());
                }
            });
        }

        assertEquals("https://www.marginalia.nu/", sampleData.get("request"));
        assertEquals("https://www.marginalia.nu/", sampleData.get("response"));
    }

    @Test
    public void flagAsSkipped() throws IOException, URISyntaxException {

        try (var recorder = new WarcRecorder(fileNameWarc, new Cookies())) {
            recorder.writeReferenceCopy(new EdgeUrl("https://www.marginalia.nu/"),
                    "text/html",
                    200,
                    "<?doctype html><html><body>test</body></html>",
                    null,
                    ContentTags.empty());
        }

        try (var reader = new WarcReader(fileNameWarc)) {
            for (var record : reader) {
                if (record instanceof WarcResponse rsp) {
                    assertEquals("https://www.marginalia.nu/", rsp.target());
                    assertEquals("text/html", rsp.contentType().type());
                    assertEquals(200, rsp.http().status());
                    assertEquals("1", rsp.http().headers().first("X-Cookies").orElse(null));
                }
            }
        }
    }

    @Test
    public void flagAsSkippedNullBody() throws IOException, URISyntaxException {

        try (var recorder = new WarcRecorder(fileNameWarc, new Cookies())) {
            recorder.writeReferenceCopy(new EdgeUrl("https://www.marginalia.nu/"),
                    "text/html",
                    200,
                    null,
                    null, ContentTags.empty());
        }

    }

    @Test
    public void testSaveImport() throws URISyntaxException, IOException {
        try (var recorder = new WarcRecorder(fileNameWarc, new Cookies())) {
            recorder.writeReferenceCopy(new EdgeUrl("https://www.marginalia.nu/"),
                    "text/html",
                    200,
                    "<?doctype html><html><body>test</body></html>",
                    null, ContentTags.empty());
        }

        try (var reader = new WarcReader(fileNameWarc)) {
            WarcXResponseReference.register(reader);

            for (var record : reader) {
                System.out.println(record.type());
                System.out.println(record.getClass().getSimpleName());
                if (record instanceof WarcXResponseReference rsp) {
                    assertEquals("https://www.marginalia.nu/", rsp.target());
                }
            }
        }

    }

    @Test
    public void testConvertToParquet() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        client.fetch(httpClient, HttpRequest.newBuilder()
                .uri(new java.net.URI("https://www.marginalia.nu/"))
                .header("User-agent", "test.marginalia.nu")
                .header("Accept-Encoding", "gzip")
                .GET().build());

        client.fetch(httpClient, HttpRequest.newBuilder()
                .uri(new java.net.URI("https://www.marginalia.nu/log/"))
                .header("User-agent", "test.marginalia.nu")
                .header("Accept-Encoding", "gzip")
                .GET().build());

        client.fetch(httpClient, HttpRequest.newBuilder()
                .uri(new java.net.URI("https://www.marginalia.nu/sanic.png"))
                .header("User-agent", "test.marginalia.nu")
                .header("Accept-Encoding", "gzip")
                .GET().build());

        CrawledDocumentParquetRecordFileWriter.convertWarc(
                "www.marginalia.nu",
                new UserAgent("test", "test"),
                fileNameWarc,
                fileNameParquet);

        var urls = CrawledDocumentParquetRecordFileReader.stream(fileNameParquet).map(doc -> doc.url).toList();
        assertEquals(2, urls.size());
        assertEquals("https://www.marginalia.nu/", urls.get(0));
        assertEquals("https://www.marginalia.nu/log/", urls.get(1));
        // sanic.jpg gets filtered out for its bad mime type

    }

}