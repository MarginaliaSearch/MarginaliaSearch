package nu.marginalia.integration;

import com.google.gson.Gson;
import nu.marginalia.model.gson.GsonFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Consumer;

public class MarginaliaApiClient implements AutoCloseable {
    private final HttpClient client;
    private final String apiKey;
    private final Gson gson = GsonFactory.get();

    public MarginaliaApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder().build();
    }

    public class ParamCtx {
        List<MarginaliaQueryParam> params = new ArrayList<>();

        public void query(String query) { params.add(new MarginaliaQueryParam.Query(query)); }
        public void count(int count) { params.add(new MarginaliaQueryParam.Count(count)); }
        public void nsfw(int nsfw) { params.add(new MarginaliaQueryParam.NsfwFlag(nsfw)); }
        public void page(int page) { params.add(new MarginaliaQueryParam.Page(page)); }
    }

    public ApiResponse query(Consumer<ParamCtx> capture) throws IOException {
        var ctx = new ParamCtx();
        capture.accept(ctx);
        return query(ctx.params);
    }

    public ApiResponse query(List<MarginaliaQueryParam> params) throws IOException {
        StringJoiner paramsJoiner = new StringJoiner("&", "?", "");

        for (var param: params) {
            paramsJoiner.add(param.asQueryElement());
        }

        final URI uri = URI.create("https://api2.marginalia-search.com/search" + paramsJoiner.toString());

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