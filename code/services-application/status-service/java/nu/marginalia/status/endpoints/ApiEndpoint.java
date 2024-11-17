package nu.marginalia.status.endpoints;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class ApiEndpoint {
    private final HttpClient client = HttpClient.newHttpClient();
    private final URI probeURI;
    private final Gson gson = GsonFactory.get();
    private static final Logger logger = LoggerFactory.getLogger(ApiEndpoint.class);

    @Inject
    public ApiEndpoint(@Named("apiTestQuery") String uri) {
        probeURI = URI.create(uri);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean check() {
        // Check if the search service is up
        var request = HttpRequest.newBuilder(probeURI).build();

        // Check if the search service is healthy
        try {
            var rsp = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (rsp.statusCode() != 200) {
                logger.info("Bad http status {}", rsp.statusCode());
                return false;
            }

            var body = rsp.body();

            Map output = gson.fromJson(body, Map.class);
            var results = (List<?>) output.getOrDefault("results", List.of());
            if (!results.isEmpty())
            {
                return true;
            }

        } catch (Exception e) {
            logger.error("Failed to call API endpoint", e);

            return false;
        }

        return false;
    }
}
