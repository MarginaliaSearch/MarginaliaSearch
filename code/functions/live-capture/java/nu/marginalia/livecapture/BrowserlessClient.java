package nu.marginalia.livecapture;

import com.google.gson.Gson;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Client for local browserless.io API */
public class BrowserlessClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BrowserlessClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final URI browserlessURI;
    private final Gson gson = GsonFactory.get();

    public BrowserlessClient(URI browserlessURI) {
        this.browserlessURI = browserlessURI;
    }

    public String content(String url, GotoOptions gotoOptions) throws IOException, InterruptedException {
        Map<String, Object> requestData = Map.of(
                "url", url,
                "gotoOptions", gotoOptions
        );

        var request = HttpRequest.newBuilder()
                .uri(browserlessURI.resolve("/content"))
                .method("POST", HttpRequest.BodyPublishers.ofString(
                        gson.toJson(requestData)
                ))
                .header("Content-type", "application/json")
                .build();

        var rsp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (rsp.statusCode() >= 300) {
            logger.info("Failed to fetch content for {}, status {}", url, rsp.statusCode());
            return null;
        }

        return rsp.body();
    }

    public byte[] screenshot(String url, GotoOptions gotoOptions, ScreenshotOptions screenshotOptions)
            throws IOException, InterruptedException {

        Map<String, Object> requestData = Map.of(
                "url", url,
                "options", screenshotOptions,
                "gotoOptions", gotoOptions
        );

        var request = HttpRequest.newBuilder()
                .uri(browserlessURI.resolve("/screenshot"))
                .method("POST", HttpRequest.BodyPublishers.ofString(
                        gson.toJson(requestData)
                ))
                .header("Content-type", "application/json")
                .build();

        var rsp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (rsp.statusCode() >= 300) {
            logger.info("Failed to fetch screenshot for {}, status {}", url, rsp.statusCode());
        }

        return rsp.body();

    }

    @Override
    public void close() throws Exception {
        httpClient.shutdownNow();
    }

    public record ScreenshotOptions(boolean fullPage, String type) {
        public static ScreenshotOptions defaultValues() {
            return new ScreenshotOptions(false, "png");
        }
    }

    public record GotoOptions(String waitUntil, long timeout) {
        public static GotoOptions defaultValues() {
            return new GotoOptions("networkidle2", Duration.ofSeconds(10).toMillis());
        }
    }

}
