package nu.marginalia.gemini.plugins;

import com.google.inject.Inject;
import nu.marginalia.gemini.io.GeminiConnection;
import nu.marginalia.gemini.io.GeminiStatusCode;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class SearchPlugin implements Plugin {
    private final PoolingHttpClientConnectionManager connectionManager;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchPlugin() {

        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);
        connectionManager.setDefaultMaxPerRoute(20);
        HttpHost host = new HttpHost("https://search.marginalia.nu/");
        connectionManager.setMaxPerRoute(new HttpRoute(host), 20);
    }

    @Override
    public boolean serve(URI url, GeminiConnection connection) throws IOException {
        var client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        if (!"/search".equals(url.getPath())) {
            return false;
        }

        String query = url.getRawQuery();

        if (null == query || "".equals(query)) {
            logger.info("Requesting search terms");
            connection.writeStatusLine(GeminiStatusCode.INPUT, "Please enter a search query");
        }
        else {
            logger.info("Delegating search query '{}'", query);

            final HttpGet get = new HttpGet(createSearchUri(query));
            final byte[] binaryResponse;

            try (var rsp = client.execute(get)) {
                binaryResponse = rsp.getEntity().getContent().readAllBytes();
            }
            catch (IOException ex) {
                logger.error("backend error", ex);

                connection.writeStatusLine(GeminiStatusCode.PROXY_ERROR, "Failed to reach backend server");
                return true;
            }

            connection
                    .writeStatusLine(GeminiStatusCode.SUCCESS, "text/gemini")
                    .writeBytes(binaryResponse);
        }
        return true;
    }

    private URI createSearchUri(String query) {
        try {
            return new URI("https://search.marginalia.nu/search?format=gmi&query="+query);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
