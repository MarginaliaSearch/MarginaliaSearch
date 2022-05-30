package nu.marginalia.wmsa.edge;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.edge.assistant.dict.WikiArticles;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.Driver;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static nu.marginalia.wmsa.configuration.ServiceDescriptor.ENCYCLOPEDIA;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Tag("e2e")
@Testcontainers
public class EncyclopediaE2ETest extends E2ETestBase {
    @Container
    public MariaDBContainer<?> mariaDB = getMariaDBContainer();

    @Container
    public GenericContainer<?> encyclopediaContainer =  forService(ENCYCLOPEDIA, mariaDB);
    @Container
    public GenericContainer<?> encyclopediaLoader = new GenericContainer<>("openjdk:17")
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

    private Gson gson = new GsonBuilder().create();
    private OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .readTimeout(6000, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build();

    private Path getModelData() {
        return Path.of(System.getProperty("user.dir")).resolve("data/test");
    }

    @Test
    public void run() throws MalformedURLException {
        new Driver();

        try (var conn = DriverManager.getConnection(mariaDB.getJdbcUrl(), "wmsa", "wmsa");
             var stmt = conn.prepareStatement("INSERT IGNORE INTO REF_WIKI_TITLE(NAME,REF_NAME) VALUES (?,?)")) {

            stmt.setString(1, "Forg");
            stmt.setString(2, "Frog");
            stmt.executeUpdate();

            stmt.setString(1, "Frog");
            stmt.setNull(2, Types.VARCHAR);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/wiki/Frog");
        System.out.println(driver.getTitle());
        driver.get("http://proxyNginx/wiki-search?query=Forg");
        System.out.println(driver.getTitle());

        assertTrue(get(encyclopediaContainer.getHost(),
                encyclopediaContainer.getMappedPort(ENCYCLOPEDIA.port),
                "/wiki/has?url=Frog", Boolean.class));

        assertFalse(get(encyclopediaContainer.getHost(),
                encyclopediaContainer.getMappedPort(ENCYCLOPEDIA.port),
                "/wiki/has?url=Marginalia", Boolean.class));

        assertFalse(get(encyclopediaContainer.getHost(),
                encyclopediaContainer.getMappedPort(ENCYCLOPEDIA.port),
                "/wiki/has?url=Marginalia", Boolean.class));



        var resultsForMarginalia = get(encyclopediaContainer.getHost(),
                encyclopediaContainer.getMappedPort(ENCYCLOPEDIA.port),
                "/encyclopedia/Marginalia", WikiArticles.class);
        Assertions.assertTrue(resultsForMarginalia.getEntries().isEmpty());

        var resultsForFrog = get(encyclopediaContainer.getHost(),
                encyclopediaContainer.getMappedPort(ENCYCLOPEDIA.port),
                "/encyclopedia/Frog", WikiArticles.class);
        Assertions.assertFalse(resultsForFrog.getEntries().isEmpty());

        var resultsForFoRg = get(encyclopediaContainer.getHost(),
                encyclopediaContainer.getMappedPort(ENCYCLOPEDIA.port),
                "/encyclopedia/Forg", WikiArticles.class);
        Assertions.assertFalse(resultsForFoRg.getEntries().isEmpty());


    }


    private <T> T get(String host, Integer mappedPort, String path, Class<T> clazz) throws MalformedURLException {
        var req = new Request.Builder().get().url(new URL("http", host, mappedPort, path)).build();
        var call = httpClient.newCall(req);
        try (var rsp = call.execute()) {
            return gson.fromJson(rsp.body().charStream(), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
