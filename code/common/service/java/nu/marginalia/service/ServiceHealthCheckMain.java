package nu.marginalia.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Curl-free health check helper for services.
 *
 * Can be installed like
 *
 *     healthcheck:
 *       test: ["CMD-SHELL", "JAVA_TOOL_OPTIONS=\"\" JDK_JAVA_OPTIONS=\"\" java -Xmx64m -cp @/app/jib-classpath-file nu.marginalia.service.ServiceHealthCheckMain /internal/ready"]
 *       interval: 1m30s
 *       timeout: 10s
 *       retries: 3
 *       start_period: 300s
 *       start_interval: 15s
 *
 */
public class ServiceHealthCheckMain {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: ServiceHealthCheckMain <endpoint>");
            System.exit(1);
        }

        String endpoint = args[0];
        String uriBase = "";

        if (Files.exists(Path.of("/app")) && endpoint.startsWith("/")) { // "in docker"
            if (!Files.exists(Path.of("/tmp/rest-addr"))) {
                System.out.println("No /tmp/rest-addr file found.");

                System.exit(1);
            }
            try {
                // In docker, on ipvlan interfaces, the service programmatically decides on a
                // non-public interface to bind to, so we need to read the address from a file
                uriBase = Files.readString(Path.of("/tmp/rest-addr"));
            } catch (IOException e) {
                e.printStackTrace();

                System.exit(1);
            }
        }

        String url = uriBase + endpoint;

        System.out.println("Health check: '" + url + "'");

        HttpClient client = HttpClient.newBuilder()
                .executor(Executors.newSingleThreadExecutor())
                .build();

        try (client) {
            int code = client.send(HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .timeout(Duration.ofSeconds(5))
                .build(), HttpResponse.BodyHandlers.discarding())
                    .statusCode();
            if (code < 300) {
                System.out.println("Health check OK");
                System.exit(0);
            }
            else {
                System.out.println("Health check failed: " + code);
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
