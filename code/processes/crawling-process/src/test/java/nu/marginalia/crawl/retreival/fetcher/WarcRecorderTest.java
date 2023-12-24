package nu.marginalia.crawl.retreival.fetcher;

import nu.marginalia.UserAgent;
import nu.marginalia.crawl.retreival.fetcher.socket.IpInterceptingNetworkInterceptor;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawling.parquet.CrawledDocumentParquetRecordFileReader;
import nu.marginalia.crawling.parquet.CrawledDocumentParquetRecordFileWriter;
import nu.marginalia.model.EdgeUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netpreserve.jwarc.*;

import java.io.IOException;
import java.net.URISyntaxException;
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
    OkHttpClient httpClient;
    @BeforeEach
    public void setUp() throws Exception {
        httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new IpInterceptingNetworkInterceptor())
                .build();

        fileNameWarc = Files.createTempFile("test", ".warc");
        fileNameParquet = Files.createTempFile("test", ".parquet");

        client = new WarcRecorder(fileNameWarc);
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
        Files.delete(fileNameWarc);
    }

    @Test
    void fetch() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        client.fetch(httpClient, new Request.Builder().url("https://www.marginalia.nu/")
                .addHeader("User-agent", "test.marginalia.nu")
                .addHeader("Accept-Encoding", "gzip")
                .get().build());

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

        try (var recorder = new WarcRecorder(fileNameWarc)) {
            recorder.flagAsSkipped(new EdgeUrl("https://www.marginalia.nu/"),
                    "text/html",
                    200,
                    "<?doctype html><html><body>test</body></html>");
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

        try (var recorder = new WarcRecorder(fileNameWarc)) {
            recorder.flagAsSkipped(new EdgeUrl("https://www.marginalia.nu/"),
                    "text/html",
                    200,
                    null);
        }

    }

    @Test
    public void testSaveImport() throws URISyntaxException, IOException {
        try (var recorder = new WarcRecorder(fileNameWarc)) {
            recorder.flagAsSkipped(new EdgeUrl("https://www.marginalia.nu/"),
                    "text/html",
                    200,
                    "<?doctype html><html><body>test</body></html>");
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
        client.fetch(httpClient, new Request.Builder().url("https://www.marginalia.nu/")
                .addHeader("User-agent", "test.marginalia.nu")
                .addHeader("Accept-Encoding", "gzip")
                .get().build());
        client.fetch(httpClient, new Request.Builder().url("https://www.marginalia.nu/log/")
                .addHeader("User-agent", "test.marginalia.nu")
                .addHeader("Accept-Encoding", "gzip")
                .get().build());
        client.fetch(httpClient, new Request.Builder().url("https://www.marginalia.nu/sanic.png")
                .addHeader("User-agent", "test.marginalia.nu")
                .addHeader("Accept-Encoding", "gzip")
                .get().build());
        client.close();

        CrawledDocumentParquetRecordFileWriter.convertWarc(
                "www.marginalia.nu",
                new UserAgent("test"),
                fileNameWarc,
                fileNameParquet);

        var urls = CrawledDocumentParquetRecordFileReader.stream(fileNameParquet).map(doc -> doc.url).toList();
        assertEquals(3, urls.size());
        assertEquals("https://www.marginalia.nu/", urls.get(0));
        assertEquals("https://www.marginalia.nu/log/", urls.get(1));
        assertEquals("https://www.marginalia.nu/sanic.png", urls.get(2));

    }

}