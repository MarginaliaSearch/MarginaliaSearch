package nu.marginalia.nsfw.document;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import nu.marginalia.model.gson.GsonFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MarginaliaApiClient implements AutoCloseable {
    private final HttpClient client;
    private final String apiKey;
    private final Gson gson = GsonFactory.get();

    public MarginaliaApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder().build();
    }

    public ApiResponse query(String query, int page, int count, int nsfwFlag) throws IOException {
        final URI uri = URI.create("https://api2.marginalia-search.com/search?query=%s&count=%d&nsfw=%d&page=%d".formatted(URLEncoder.encode(query, StandardCharsets.UTF_8), count, nsfwFlag, page));

        System.out.println(uri);

        try {
            var rsp = client.send(HttpRequest.newBuilder(uri).GET().header("API-Key", apiKey).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (rsp.statusCode() == 200) {
                return gson.fromJson(rsp.body(), ApiResponse.class);
            }
            else {
                throw new IOException("Bad status code " + rsp.statusCode() + " " + rsp.body());
            }
        } catch (InterruptedException e) {
            throw new IOException("Request interrupted", e);
        }
    }

    @Override
    public void close() {
        client.close();
    }

    public record ApiResponse(List<SearchResult> results, int pages) {}
    public record SearchResult(String url, String title, String description) {}
}
