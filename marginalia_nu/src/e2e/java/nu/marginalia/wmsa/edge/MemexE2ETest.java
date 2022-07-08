package nu.marginalia.wmsa.edge;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.Driver;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static nu.marginalia.wmsa.configuration.ServiceDescriptor.AUTH;
import static nu.marginalia.wmsa.configuration.ServiceDescriptor.MEMEX;

@Tag("e2e")
@Testcontainers
public class MemexE2ETest extends E2ETestBase {
    @Container
    public MariaDBContainer<?> mariaDB = getMariaDBContainer();

    @Container
    public GenericContainer<?> auth = forService(AUTH, mariaDB);

    @Container
    public GenericContainer<?> memexContainer =  forService(MEMEX, mariaDB, "memex.sh")
            .withClasspathResourceMapping("/memex", "/memex", BindMode.READ_ONLY);

    @Container
    public NginxContainer<?> proxyNginx = new NginxContainer<>("nginx:stable")
            .dependsOn(auth)
            .dependsOn(memexContainer)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("nginx")))
            .withCopyFileToContainer(MountableFile.forClasspathResource("nginx/memex.conf"), "/etc/nginx/conf.d/default.conf")
            .withNetwork(network)
            .withNetworkAliases("proxyNginx");

    @Container
    public BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>()
            .withNetwork(network)
            .withCapabilities(new ChromeOptions());

    private Gson gson = new GsonBuilder().create();
    private OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .readTimeout(6000, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build();

    @Test
    public void run() throws IOException, InterruptedException {
        Thread.sleep(10_000);
        new Driver();

        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/");
        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("frontpage"));

        driver.get("http://proxyNginx/log/");
        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("log"));

        driver.get("http://proxyNginx/log/a.gmi");
        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("log-a.gmi"));

        driver.get("http://proxyNginx/log/b.gmi");
        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("log-b.gmi"));
    }

    private static Path screenshotFilename(String operation) throws IOException {
        var path = Path.of(System.getProperty("user.dir")).resolve("build/test/e2e/");
        Files.createDirectories(path);

        String name = String.format("test-%s-%s.png", operation, LocalDateTime.now());
        path = path.resolve(name);

        System.out.println("Screenshot in " + path);
        return path;
    }


}
