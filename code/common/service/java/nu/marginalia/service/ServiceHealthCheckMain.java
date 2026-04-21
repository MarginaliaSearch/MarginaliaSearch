package nu.marginalia.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Curl-free health check helper for services.
 *
 * Can be installed like
 *
 *     healthcheck:
 *       test: ["CMD-SHELL", "JAVA_TOOL_OPTIONS=\"\" JDK_JAVA_OPTIONS=\"\" java -Xmx64m -cp @/app/jib-classpath-file nu.marginalia.service.ServiceHealthCheckMain http://localhost:80/internal/ready"]
 *       interval: 1m30s
 *       timeout: 10s
 *       retries: 3
 *       start_period: 300s
 *       start_interval: 15s
 *
 */
public class ServiceHealthCheckMain {
    public static void main(String[] args) {
        String url = args[0];

        System.out.println("Health check: " + url);

        HttpClient client = HttpClient.newBuilder()
                .executor(Executors.newSingleThreadExecutor())
                .build();

        try (client) {
            int code = client.send(HttpRequest.newBuilder()
                        .uri(new URI(args[0]))
                        .timeout(Duration.ofSeconds(5))
                .build(), HttpResponse.BodyHandlers.discarding())
                    .statusCode();
            if (code < 300) {
                System.out.println("Health check OK");
                System.exit(0);
            }
            else {
                System.err.println("Health check failed: " + code);
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
