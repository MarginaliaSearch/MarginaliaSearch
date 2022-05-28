package nu.marginalia.wmsa.edge;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.NginxContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;

import static nu.marginalia.wmsa.configuration.ServiceDescriptor.ENCYCLOPEDIA;

@Tag("e2e")
@Testcontainers
public class EncyclopediaE2ETest extends E2ETestBase {
    @Container
    public GenericContainer<?> mariaDB = getMariaDBContainer();

    @Container
    public GenericContainer<?> encyclopediaContainer =  forService(ENCYCLOPEDIA, mariaDB);
    @Container
    public GenericContainer<?> encyclopediaLoader = new GenericContainer<>("openjdk:17-alpine")
            .dependsOn(encyclopediaContainer)
            .dependsOn(mariaDB)
            .withNetwork(network)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("encyclopedia-loader")))
            .withCopyFileToContainer(jarFile(), "/WMSA.jar")
            .withCopyFileToContainer(MountableFile.forClasspathResource("load-encyclopedia.sh"), "/load-encyclopedia.sh")
            .withFileSystemBind(getModelData().toString(), "/data", BindMode.READ_ONLY)
            .withCommand("sh", "load-encyclopedia.sh")
            .waitingFor(Wait.forLogMessage(".*ALL DONE.*", 1).withStartupTimeout(Duration.ofMinutes(10)));

    @Container
    public NginxContainer<?> proxyNginx = new NginxContainer<>("nginx:stable")
            .dependsOn(encyclopediaLoader)
            .dependsOn(encyclopediaContainer)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("nginx")))
            .withCopyFileToContainer(MountableFile.forClasspathResource("nginx/encyclopedia.conf"), "/etc/nginx/conf.d/default.conf")
            .withNetwork(network)
            .withNetworkAliases("proxyNginx");

    @Container
    public BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>()
            .withNetwork(network)
            .withCapabilities(new ChromeOptions());

    private Path getModelData() {
        return Path.of(System.getProperty("user.dir")).resolve("data/test");
    }

    @Test
    public void run() {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/wiki/Frog");
        System.out.println(driver.getTitle());
        System.out.println(driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML"));
    }
}
