package nu.marginalia.crawl.retreival.fetcher;

import nu.marginalia.crawl.retreival.fetcher.socket.IpInterceptingNetworkInterceptor;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarcRecorderTest {
    Path fileName;
    WarcRecorder client;
    OkHttpClient httpClient;
    @BeforeEach
    public void setUp() throws Exception {
        httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new IpInterceptingNetworkInterceptor())
                .build();

        fileName = Files.createTempFile("test", ".warc.gz");
        client = new WarcRecorder(fileName);
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
        Files.delete(fileName);
    }

    @Test
    void fetch() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        client.fetch(httpClient, new Request.Builder().url("https://www.marginalia.nu/")
                .addHeader("User-agent", "test.marginalia.nu")
                .addHeader("Accept-Encoding", "gzip")
                .get().build());

        new GZIPInputStream(Files.newInputStream(fileName)).transferTo(System.out);

        Map<String, String> sampleData = new HashMap<>();
        try (var warcReader = new WarcReader(fileName)) {
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

        try (var recorder = new WarcRecorder(fileName)) {
            recorder.flagAsSkipped(new EdgeUrl("https://www.marginalia.nu/"),
                    """
                            Content-type: text/html
                            X-Cookies: 1
                            """,
                    200,
                    "<?doctype html><html><body>test</body></html>");
        }

        try (var reader = new WarcReader(fileName)) {
            for (var record : reader) {
                if (record instanceof WarcResponse rsp) {
                    assertEquals("https://www.marginalia.nu/", rsp.target());
                    assertEquals("text/html", rsp.contentType().type());
                    assertEquals(200, rsp.http().status());
                    assertEquals("1", rsp.http().headers().first("X-Cookies").orElse(null));
                }
            }
        }

        new GZIPInputStream(Files.newInputStream(fileName)).transferTo(System.out);
    }


}