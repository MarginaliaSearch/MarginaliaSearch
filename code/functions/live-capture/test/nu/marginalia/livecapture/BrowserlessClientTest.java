package nu.marginalia.livecapture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.Map;

@Testcontainers
@Tag("slow")
public class BrowserlessClientTest {
    static GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("browserless/chrome"))
            .withEnv(Map.of("TOKEN", "BROWSERLESS_TOKEN"))
            .withExposedPorts(3000);

    @BeforeAll
    public static void setup() {
        container.start();
    }

    @Test
    public void testContent() throws Exception {
        try (var client = new BrowserlessClient(URI.create("http://" + container.getHost() + ":" + container.getMappedPort(3000)))) {
            var content = client.content("https://www.marginalia.nu/", BrowserlessClient.GotoOptions.defaultValues());
            Assertions.assertNotNull(content, "Content should not be null");
            Assertions.assertFalse(content.isBlank(), "Content should not be empty");
        }
    }

    @Test
    public void testScreenshot() throws Exception {
        try (var client = new BrowserlessClient(URI.create("http://" + container.getHost() + ":" + container.getMappedPort(3000)))) {
            var screenshot = client.screenshot("https://www.marginalia.nu/", BrowserlessClient.GotoOptions.defaultValues(), BrowserlessClient.ScreenshotOptions.defaultValues());
            Assertions.assertNotNull(screenshot, "Screenshot should not be null");
        }
    }
}
