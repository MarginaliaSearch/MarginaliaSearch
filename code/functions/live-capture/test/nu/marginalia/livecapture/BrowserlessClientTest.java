package nu.marginalia.livecapture;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import nu.marginalia.WmsaHome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.Map;import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.net.NetworkInterface.getNetworkInterfaces;

@Testcontainers
@Tag("slow")
public class BrowserlessClientTest {
    static GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("browserless/chrome"))
            .withEnv(Map.of("TOKEN", "BROWSERLESS_TOKEN"))
            .withNetworkMode("bridge")
            .withExposedPorts(3000);

    static WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(18089));

    static String localIp;
    @BeforeAll
    public static void setup() throws IOException {
        container.start();

        wireMockServer.start();
        wireMockServer.stubFor(get("/").willReturn(aResponse().withStatus(200).withBody("Ok")));

        localIp = findLocalIp();
    }

    private static String findLocalIp() throws SocketException {
        var interfaces = getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            var iface = interfaces.nextElement();
            if (iface.isLoopback())
                continue;
            else if (iface.isVirtual())
                continue;

            var addresses = iface.getInetAddresses();

            while (addresses.hasMoreElements()) {
                var address = addresses.nextElement();

                if (!address.isSiteLocalAddress()) continue;

                return address.getHostAddress();
            }
        }

        return "127.0.0.1";
    }

    @Tag("flaky")
    @Test
    public void testInspectContentUA__Flaky() throws Exception {
        try (var client = new BrowserlessClient(URI.create("http://" + container.getHost() + ":" + container.getMappedPort(3000)))) {
            client.content("http://" + localIp + ":18089/", BrowserlessClient.GotoOptions.defaultValues());
        }

        wireMockServer.verify(getRequestedFor(urlEqualTo("/")).withHeader("User-Agent", equalTo(WmsaHome.getUserAgent().uaString())));
    }

    @Tag("flaky")
    @Test
    public void testInspectScreenshotUA__Flaky() throws Exception {
        try (var client = new BrowserlessClient(URI.create("http://" + container.getHost() + ":" + container.getMappedPort(3000)))) {
            client.screenshot("http://" + localIp + ":18089/", BrowserlessClient.GotoOptions.defaultValues(), BrowserlessClient.ScreenshotOptions.defaultValues());
        }

        wireMockServer.verify(getRequestedFor(urlEqualTo("/")).withHeader("User-Agent", equalTo(WmsaHome.getUserAgent().uaString())));
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
