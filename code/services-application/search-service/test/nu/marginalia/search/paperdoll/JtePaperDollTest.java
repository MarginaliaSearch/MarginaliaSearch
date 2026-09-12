package nu.marginalia.search.paperdoll;

import io.jooby.ServerOptions;
import io.jooby.netty.NettyServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class JtePaperDollTest {
    @Test
    void servesPreviewRoutesAndStaticAssets() throws Exception {
        var previousTestEnv = System.getProperty("test-env");
        var server = new NettyServer(new ServerOptions().setHost("127.0.0.1").setPort(0)
                .setWorkerThreads(2).setIoThreads(2));
        try (var client = HttpClient.newHttpClient()) {
            server.start(new JtePaperDoll().createApp());
            var baseUrl = "http://127.0.0.1:" + server.getOptions().getPort();
            for (var route : new String[][]{
                    {"/suggest/", "application/json", "plaa"},
                    {"/", "text/html", "<html"},
                    {"/site-info?view=links", "text/html", "<html"},
                    {"/screenshot/example.org", "image/svg+xml", "<svg"},
                    {"/css/style.css", "text/css", "{"}
            }) {
                var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + route[0])).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), route[0]);
                assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith(route[1]));
                assertTrue(response.body().contains(route[2]));
                assertTrue(response.headers().firstValue("Content-Encoding").isEmpty());
            }
        } finally {
            server.stop();
            if (previousTestEnv == null) System.clearProperty("test-env");
            else System.setProperty("test-env", previousTestEnv);
        }
    }
}
