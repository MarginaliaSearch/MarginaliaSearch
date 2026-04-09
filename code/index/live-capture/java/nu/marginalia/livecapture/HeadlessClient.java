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

/** Client for local headless browser */
public class HeadlessClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(HeadlessClient.class);
    private static final String BROWSERLESS_TOKEN = System.getProperty("live-capture.headless-token", "HEADLESS_TOKEN");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final URI headlessURI;
    private final Gson gson = GsonFactory.get();

    private final String userAgent = WmsaHome.getUserAgent().uaString();

    public HeadlessClient(URI headlessURI) {
        this.headlessURI = headlessURI;
    }

    /** Fetches content with a marginalia hack extension loaded that decorates the DOM with attributes for
     * certain CSS attributes, to be able to easier identify popovers and other nuisance elements.
     */
    public Optional<String> domSample(String url) throws IOException, InterruptedException {
        Map<String, Object> requestData = Map.of(
                "url", url
        );

        var request = HttpRequest.newBuilder()
                .uri(headlessURI.resolve("/dom-sample?token="+BROWSERLESS_TOKEN))
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

    public byte[] screenshot(String url)
            throws IOException, InterruptedException {

        Map<String, Object> requestData = Map.of(
                "url", url
        );

        var request = HttpRequest.newBuilder()
                .uri(headlessURI.resolve("/screenshot?token="+BROWSERLESS_TOKEN))
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

}
