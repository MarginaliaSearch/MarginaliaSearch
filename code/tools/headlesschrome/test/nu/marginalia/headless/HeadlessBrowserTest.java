package nu.marginalia.headless;


import com.google.gson.Gson;
import io.jooby.StatusCode;
import nu.marginalia.model.gson.GsonFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class HeadlessBrowserTest {
    private static final Gson gson = GsonFactory.get();

    private static GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("marginalia-headless"))
            .withEnv(Map.of("TOKEN", "HEADLESS_TOKEN"))
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withNetworkMode("bridge")
            .withLogConsumer(frame -> {
                System.out.print(frame.getUtf8String());
            })
            .withExposedPorts(8080)
            .waitingFor(Wait.forHealthcheck());



    @BeforeAll
    public static void setUpAll() throws InterruptedException {
        container.start();
    }

    @AfterAll
    public static void tearDownAll() {
        container.stop();
    }


    @Test
    public void testHealth() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var rsp = client.send(
                    HttpRequest.newBuilder(forPath("/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, rsp.statusCode());
            System.out.println(rsp.body());
        }
    }

    @Test
    public void testKill() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var rsp = client.send(
                    HttpRequest.newBuilder(forPath("/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, rsp.statusCode());
            System.out.println(rsp.body());

            rsp = client.send(
                    HttpRequest.newBuilder(forPath("/kill")).POST(HttpRequest.BodyPublishers.noBody())
                            .setHeader("Authorization", "HEADLESS_TOKEN")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, rsp.statusCode());
            System.out.println(rsp.body());

            rsp = client.send(
                    HttpRequest.newBuilder(forPath("/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertNotEquals(200, rsp.statusCode());
            System.out.println(rsp.body());
        }
    }

    @Test
    public void testDomSample() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var rsp = client.send(
                    HttpRequest.newBuilder(forPath("/dom-sample"))
                            .header("Authorization", "HEADLESS_TOKEN")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(Map.of("url", "https://www.marginalia.nu/"))
                    )).build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, rsp.statusCode());
            System.out.println(rsp.body());
        }
    }


    @Test
    public void testScreenshot() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var rsp = client.send(
                    HttpRequest.newBuilder(forPath("/screenshot"))
                            .header("Authorization", "HEADLESS_TOKEN")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(Map.of("url", "https://www.marginalia.nu/"))
                    )).build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, rsp.statusCode());
            byte[] bytes = rsp.body();
            assertEquals("PNG", new String(bytes, 1, 3, StandardCharsets.US_ASCII));

            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            assertEquals(1024, image.getWidth());
            assertEquals(768, image.getHeight());
        }
    }


    @Test
    public void testDomSample_Unauthorized() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var rsp = client.send(
                    HttpRequest.newBuilder(forPath("/dom-sample")).POST(
                            HttpRequest.BodyPublishers.ofString(gson.toJson(Map.of("url", "https://www.marginalia.nu/")))).build(),
                    HttpResponse.BodyHandlers.discarding()
            );

            assertEquals(StatusCode.UNAUTHORIZED_CODE, rsp.statusCode());
        }
    }

    @Test
    public void testScreenshot_Unauthorized() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var rsp = client.send(
                    HttpRequest.newBuilder(forPath("/screenshot")).POST(
                            HttpRequest.BodyPublishers.ofString(
                                gson.toJson(Map.of("url", "https://www.marginalia.nu/"))
                    )).build(),
                    HttpResponse.BodyHandlers.discarding()
            );

            assertEquals(StatusCode.UNAUTHORIZED_CODE, rsp.statusCode());
        }
    }

    private URI forPath(String path) {
        return URI.create("http://localhost:"+container.getMappedPort(8080)+(path.startsWith("/")?"":"/")+path);
    }

}
