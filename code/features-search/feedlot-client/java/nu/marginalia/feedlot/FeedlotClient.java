package nu.marginalia.feedlot;

import com.google.gson.Gson;
import nu.marginalia.feedlot.model.FeedItems;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;

public class FeedlotClient {
    private final String feedlotHost;
    private final int feedlotPort;
    private final Gson gson;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public FeedlotClient(String feedlotHost,
                         int feedlotPort,
                         Gson gson,
                         Duration connectTimeout,
                         Duration requestTimeout
                         )
    {
        this.feedlotHost = feedlotHost;
        this.feedlotPort = feedlotPort;
        this.gson = gson;

        httpClient = HttpClient.newBuilder()
                .executor(Executors.newCachedThreadPool())
                .connectTimeout(connectTimeout)
                .build();
        this.requestTimeout = requestTimeout;
    }

    public CompletableFuture<FeedItems> getFeedItems(String domainName) {
        return httpClient.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://%s:%d/feed/%s".formatted(feedlotHost, feedlotPort, domainName)))
                        .GET()
                        .timeout(requestTimeout)
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(HttpResponse::body)
         .thenApply(this::parseFeedItems);
    }

    private FeedItems parseFeedItems(String s) {
        return gson.fromJson(s, FeedItems.class);
    }

    public void stop() {
        httpClient.close();
    }
}
