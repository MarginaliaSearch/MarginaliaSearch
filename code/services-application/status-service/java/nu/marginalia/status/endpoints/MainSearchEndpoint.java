package nu.marginalia.status.endpoints;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MainSearchEndpoint {
    private final HttpClient client = HttpClient.newHttpClient();
    private final URI probeURI;

    private static final Logger logger = LoggerFactory.getLogger(MainSearchEndpoint.class);

    @Inject
    public MainSearchEndpoint(@Named("searchEngineTestQuery") String uri) {
        probeURI = URI.create(uri);
    }

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

            var document = Jsoup.parse(body);
            var results = document.getElementsByClass("search-result");

            if (results.size() > 10) {
                return true;
            } else {
                return false;
            }

        } catch (Exception e) {
            logger.error("Failed to call public search endpoint", e);

            return false;
        }
    }
}
