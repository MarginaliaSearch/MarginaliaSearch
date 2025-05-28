package nu.marginalia.livecapture;

import com.google.gson.Gson;
import nu.marginalia.WmsaHome;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Client for local browserless.io API */
public class BrowserlessClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(BrowserlessClient.class);
    private static final String BROWSERLESS_TOKEN = System.getProperty("live-capture.browserless-token", "BROWSERLESS_TOKEN");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final URI browserlessURI;
    private final Gson gson = GsonFactory.get();

    private final String userAgent = WmsaHome.getUserAgent().uaString();

    public BrowserlessClient(URI browserlessURI) {
        this.browserlessURI = browserlessURI;
    }

    public Optional<String> content(String url, GotoOptions gotoOptions) throws IOException, InterruptedException {
        Map<String, Object> requestData = Map.of(
                "url", url,
                "userAgent", userAgent,
                "gotoOptions", gotoOptions
        );

        var request = HttpRequest.newBuilder()
                .uri(browserlessURI.resolve("/content?token="+BROWSERLESS_TOKEN))
                .method("POST", HttpRequest.BodyPublishers.ofString(
                        gson.toJson(requestData)
                ))
                .header("Content-type", "application/json")
                .build();

        var rsp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (rsp.statusCode() >= 300) {
            logger.info("Failed to fetch content for {}, status {}", url, rsp.statusCode());
            return Optional.empty();
        }

        return Optional.of(rsp.body());
    }

    /** Fetches content with a marginalia hack extension loaded that decorates the DOM with attributes for
     * certain CSS attributes, to be able to easier identify popovers and other nuisance elements.
     */
    public Optional<String> annotatedContent(String url, GotoOptions gotoOptions) throws IOException, InterruptedException {
        Map<String, Object> requestData = Map.of(
                "url", url,
                "userAgent", userAgent,
                "gotoOptions", gotoOptions,
                "waitForSelector", Map.of("selector", "#marginaliahack", "timeout", 15000)
        );

        // Launch parameters for the browserless instance to load the extension
        Map<String, Object> launchParameters = Map.of(
                "args", List.of("--load-extension=/dom-export")
        );

        String launchParametersStr = URLEncoder.encode(gson.toJson(launchParameters), StandardCharsets.UTF_8);

        var request = HttpRequest.newBuilder()
                .uri(browserlessURI.resolve("/content?token="+BROWSERLESS_TOKEN+"&launch="+launchParametersStr))
                .method("POST", HttpRequest.BodyPublishers.ofString(
                        gson.toJson(requestData)
                ))
                .header("Content-type", "application/json")
                .build();

        var rsp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (rsp.statusCode() >= 300) {
            logger.info("Failed to fetch annotated content for {}, status {}", url, rsp.statusCode());
            return Optional.empty();
        }

        return Optional.of(rsp.body());
    }

    public byte[] screenshot(String url, GotoOptions gotoOptions, ScreenshotOptions screenshotOptions)
            throws IOException, InterruptedException {

        Map<String, Object> requestData = Map.of(
                "url", url,
                "userAgent", userAgent,
                "options", screenshotOptions,
                "gotoOptions", gotoOptions
        );

        var request = HttpRequest.newBuilder()
                .uri(browserlessURI.resolve("/screenshot?token="+BROWSERLESS_TOKEN))
                .method("POST", HttpRequest.BodyPublishers.ofString(
                        gson.toJson(requestData)
                ))
                .header("Content-type", "application/json")
                .build();

        var rsp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (rsp.statusCode() >= 300) {
            logger.info("Failed to fetch screenshot for {}, status {}", url, rsp.statusCode());
            return new byte[0];
        }

        return rsp.body();

    }

    @Override
    public void close() {
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
