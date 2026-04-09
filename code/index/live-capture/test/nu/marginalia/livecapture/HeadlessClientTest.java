package nu.marginalia.livecapture;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import nu.marginalia.WmsaHome;
import nu.marginalia.domsample.db.DomSampleDb;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;


@Testcontainers
@Tag("slow")
public class HeadlessClientTest {
    // Run gradle docker if this image is not available
    static GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("marginalia-headless"))
            .withEnv(Map.of(
                    "TOKEN", "HEADLESS_TOKEN",
                    "SOFT_KILL", "1"
                    ))
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withNetworkMode("bridge")
            .withLogConsumer(frame -> {
                System.out.print(frame.getUtf8String());
            })
            .withExposedPorts(8080);

    static WireMockServer wireMockServer =
            new WireMockServer(WireMockConfiguration.wireMockConfig()
                    .port(18089));

    static String localIp;

    static URI headlessURI;

    @BeforeAll
    public static void setup() throws IOException {
        container.start();

        headlessURI = URI.create(String.format("http://%s:%d/",
                container.getHost(),
                container.getMappedPort(8080))
        );

        wireMockServer.start();
        wireMockServer.stubFor(get("/").willReturn(aResponse().withStatus(200).withBody("Ok")));

        localIp = ServiceConfigurationModule.getLocalNetworkIP();
    }

    @Tag("flaky")
    @Test
    public void testInspectContentUA__Flaky() throws Exception {
        try (var client = new HeadlessClient(headlessURI)) {
            client.domSample("http://" + localIp + ":18089/");
        }

        wireMockServer.verify(getRequestedFor(urlEqualTo("/")).withHeader("User-Agent", equalTo(WmsaHome.getUserAgent().uaString())));
    }

    @Tag("flaky")
    @Test
    public void testInspectScreenshotUA__Flaky() throws Exception {
        try (var client = new HeadlessClient(headlessURI)) {
            client.screenshot("http://" + localIp + ":18089/");
        }

        wireMockServer.verify(getRequestedFor(urlEqualTo("/")).withHeader("User-Agent", equalTo(WmsaHome.getUserAgent().uaString())));
    }

    @Test
    public void testDomSample() throws Exception {

        try (var client = new HeadlessClient(headlessURI);
             DomSampleDb dbop = new DomSampleDb(Path.of("/tmp/dom-sample.db"))
        ) {
            var content = client.domSample("https://marginalia.nu/").orElseThrow();
            dbop.saveSample("marginalia.nu", "https://marginalia.nu/", content);
            System.out.println(content);
            Assertions.assertFalse(content.isBlank(), "Content should not be empty");

            dbop.getSamples("marginalia.nu").forEach(sample -> {
                System.out.println("Sample URL: " + sample.url());
                System.out.println("Sample Content: " + sample.sample());
                System.out.println("Sample Requests: " + sample.requests());
                System.out.println("Accepted Popover: " + sample.acceptedPopover());
            });
        }
        finally {
            Files.deleteIfExists(Path.of("/tmp/dom-sample.db"));
        }

    }

    @Test
    public void testScreenshot() throws Exception {
        try (var client = new HeadlessClient(headlessURI)) {
            var screenshot = client.screenshot("https://www.marginalia.nu/");

            Assertions.assertNotNull(screenshot, "Screenshot should not be null");
        }
    }
}
