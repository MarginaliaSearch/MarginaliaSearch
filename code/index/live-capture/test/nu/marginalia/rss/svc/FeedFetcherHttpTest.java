package nu.marginalia.rss.svc;

import com.github.tomakehurst.wiremock.WireMockServer;
import nu.marginalia.rss.model.FeedDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class FeedFetcherHttpTest {
    private final WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final FeedFetcherService fetcher = new FeedFetcherService(null, null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        server.start();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        server.stop();
    }

    @Test
    void deferRateLimit() {
        deferRetry(429);
    }

    @Test
    void deferUnavailable() {
        deferRetry(503);
    }

    private void deferRetry(int status) {
        server.stubFor(get("/feed").willReturn(aResponse()
                .withStatus(status)
                .withHeader("Retry-After", "3600")));

        var result = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                fetcher.fetchFeedData(feed(), executor, null, null));

        assertInstanceOf(FeedFetcherService.FetchResult.TransientError.class, result);
        server.verify(1, getRequestedFor(urlEqualTo("/feed")));
    }

    private FeedDefinition feed() {
        return new FeedDefinition("localhost", server.baseUrl() + "/feed", null);
    }
}
