package nu.marginalia.rss.svc;

import com.github.tomakehurst.wiremock.WireMockServer;
import nu.marginalia.rss.model.FeedDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;
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

    @Test
    void slowBody() {
        server.stubFor(get("/feed").willReturn(aResponse()
                .withHeader("Content-Type", "application/rss+xml")
                .withBody("x".repeat(100))
                .withChunkedDribbleDelay(20, 2000)));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (int i = 0; i < 3; i++) {
                var result = fetcher.fetchFeedData(feed(), executor, null, null, Duration.ofMillis(250));
                assertInstanceOf(FeedFetcherService.FetchResult.TransientError.class, result);
            }

            server.stubFor(get("/feed").willReturn(aResponse()
                    .withHeader("Content-Type", "application/rss+xml")
                    .withBody("<rss/>")));
            assertInstanceOf(FeedFetcherService.FetchResult.Success.class,
                    fetcher.fetchFeedData(feed(), executor, null, null, Duration.ofSeconds(1)));
        });
    }

    @Test
    void discardErrorBody() {
        server.stubFor(get("/feed").willReturn(aResponse()
                .withStatus(404)
                .withBody("x".repeat(100))
                .withChunkedDribbleDelay(20, 10_000)));

        var result = assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                fetcher.fetchFeedData(feed(), executor, null, null));
        assertInstanceOf(FeedFetcherService.FetchResult.PermanentError.class, result);
    }

    @Test
    void largeBody() {
        server.stubFor(get("/feed").willReturn(aResponse()
                .withHeader("Content-Type", "application/rss+xml")
                .withBody("x".repeat(FeedFetcherService.MAX_FEED_BYTES + 1))));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertInstanceOf(FeedFetcherService.FetchResult.TransientError.class,
                        fetcher.fetchFeedData(feed(), executor, null, null)));
    }

    @Test
    void largeGzipBody() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(new byte[FeedFetcherService.MAX_FEED_BYTES + 1]);
        }
        server.stubFor(get("/feed").willReturn(aResponse()
                .withHeader("Content-Type", "application/rss+xml")
                .withHeader("Content-Encoding", "gzip")
                .withBody(bytes.toByteArray())));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertInstanceOf(FeedFetcherService.FetchResult.TransientError.class,
                        fetcher.fetchFeedData(feed(), executor, null, null)));
    }

    @Test
    void conditionalRequest() throws Exception {
        server.stubFor(get("/feed").willReturn(aResponse().withStatus(304)));

        assertInstanceOf(FeedFetcherService.FetchResult.NotModified.class,
                fetcher.fetchFeedData(feed(), executor, "yesterday", "etag"));
        server.verify(getRequestedFor(urlEqualTo("/feed"))
                .withHeader("If-None-Match", equalTo("etag"))
                .withHeader("If-Modified-Since", absent()));
    }

    private FeedDefinition feed() {
        return new FeedDefinition("localhost", server.baseUrl() + "/feed", null);
    }
}
