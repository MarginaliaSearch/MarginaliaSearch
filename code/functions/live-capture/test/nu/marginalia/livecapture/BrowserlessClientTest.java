package nu.marginalia.livecapture;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import nu.marginalia.WmsaHome;
import nu.marginalia.service.module.ServiceConfigurationModule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;


@Testcontainers
@Tag("slow")
public class BrowserlessClientTest {
    // Run gradle docker if this image is not available
    static GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("marginalia-browserless"))
            .withEnv(Map.of("TOKEN", "BROWSERLESS_TOKEN"))
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withNetworkMode("bridge")
            .withLogConsumer(frame -> {
                System.out.print(frame.getUtf8String());
            })
            .withExposedPorts(3000);

    static WireMockServer wireMockServer =
            new WireMockServer(WireMockConfiguration.wireMockConfig()
                    .port(18089));

    static String localIp;

    static URI browserlessURI;
    static URI browserlessWssURI;

    @BeforeAll
    public static void setup() throws IOException {
        container.start();

        browserlessURI = URI.create(String.format("http://%s:%d/",
                container.getHost(),
                container.getMappedPort(3000))
        );

        browserlessWssURI = URI.create(String.format("ws://%s:%d/?token=BROWSERLESS_TOKEN",
                container.getHost(),
                container.getMappedPort(3000))
        );


        wireMockServer.start();
        wireMockServer.stubFor(get("/").willReturn(aResponse().withStatus(200).withBody("Ok")));

        localIp = ServiceConfigurationModule.getLocalNetworkIP();

    }

    @Tag("flaky")
    @Test
    public void testInspectContentUA__Flaky() throws Exception {
        try (var client = new BrowserlessClient(browserlessURI)) {
            client.content("http://" + localIp + ":18089/",
                    BrowserlessClient.GotoOptions.defaultValues()
            );
        }

        wireMockServer.verify(getRequestedFor(urlEqualTo("/")).withHeader("User-Agent", equalTo(WmsaHome.getUserAgent().uaString())));
    }

    @Tag("flaky")
    @Test
    public void testInspectScreenshotUA__Flaky() throws Exception {
        try (var client = new BrowserlessClient(browserlessURI)) {
            client.screenshot("http://" + localIp + ":18089/",
                    BrowserlessClient.GotoOptions.defaultValues(),
                    BrowserlessClient.ScreenshotOptions.defaultValues()
            );
        }

        wireMockServer.verify(getRequestedFor(urlEqualTo("/")).withHeader("User-Agent", equalTo(WmsaHome.getUserAgent().uaString())));
    }

    @Test
    public void testContent() throws Exception {
        try (var client = new BrowserlessClient(browserlessURI)) {
            var content = client.content("https://www.marginalia.nu/", BrowserlessClient.GotoOptions.defaultValues()).orElseThrow();

            Assertions.assertFalse(content.isBlank(), "Content should not be empty");
        }
    }

    @Test
    public void testAnnotatedContent() throws Exception {
        try (var client = new BrowserlessClient(browserlessURI)) {
            var content = client.annotatedContent("https://www.marginalia-search.com/", BrowserlessClient.GotoOptions.defaultValues()).orElseThrow();
            Assertions.assertFalse(content.isBlank(), "Content should not be empty");
        }
    }

    @Test
    public void testScreenshot() throws Exception {
        try (var client = new BrowserlessClient(browserlessURI)) {
            var screenshot = client.screenshot("https://www.marginalia.nu/",
                    BrowserlessClient.GotoOptions.defaultValues(),
                    BrowserlessClient.ScreenshotOptions.defaultValues());

            Assertions.assertNotNull(screenshot, "Screenshot should not be null");
        }
    }
}
