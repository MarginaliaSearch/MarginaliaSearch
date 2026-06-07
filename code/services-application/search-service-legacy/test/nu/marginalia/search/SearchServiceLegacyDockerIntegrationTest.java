package nu.marginalia.search;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
public class SearchServiceLegacyDockerIntegrationTest {
    private static final String dbProperties = """
            db.user=wmsa
            db.pass=wmsa
            db.conn=jdbc:mariadb://mariadb:3306/WMSA_prod?rewriteBatchedStatements=true
            """;

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetwork(Network.SHARED)
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    static String runDir = getRunDir();

    private static String getRunDir() {
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        Path root = Path.of("/");

        for (int iters = 0; iters < 10; iters++) {
            if (Files.isDirectory(cwd.resolve("run/data"))) {
                System.out.println("Found run directory: " + cwd);

                return cwd.resolve("run").toString();
            }

            cwd = cwd.getParent();

            if (cwd.normalize().equals(root)) {
                throw new IllegalStateException("Could not find run directory");
            }
        }

        throw new IllegalStateException("Could not find run directory");
    }

    private static final GenericContainer<?> zookeeper = new GenericContainer<>("zookeeper:3.8")
            .withNetworkAliases("zookeeper")
            .withNetwork(Network.SHARED);

    private static GenericContainer<?> controlContainer = new GenericContainer<>(DockerImageName.parse("marginalia/control-service"))
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withNetworkAliases("control-service")
            .withNetwork(Network.SHARED)
            .withEnv(Map.of("ZOOKEEPER_HOSTS", "zookeeper:2181",
                    "SERVICE_HOST", "control-service"
                    ))
            .withFileSystemBind(runDir + "/data", "/wmsa/data", BindMode.READ_ONLY)
            .withFileSystemBind(runDir + "/model", "/wmsa/model", BindMode.READ_ONLY)
            .withLogConsumer(frame -> {
                System.out.print(frame.getUtf8String());
            })
            .withCopyToContainer(Transferable.of(dbProperties), "/wmsa/conf/db.properties")
            .withExposedPorts(80)
            .waitingFor(Wait.forHttp("/internal/ready").withStartupTimeout(Duration.ofMinutes(5)));

    private static GenericContainer<?> assistantContainer = new GenericContainer<>(DockerImageName.parse("marginalia/assistant-service"))
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withNetworkAliases("assistant-service")
            .withNetwork(Network.SHARED)
            .withEnv(Map.of("ZOOKEEPER_HOSTS", "zookeeper:2181",
                    "SERVICE_HOST", "assistant-service"
            ))
            .withFileSystemBind(runDir + "/data", "/wmsa/data", BindMode.READ_ONLY)
            .withFileSystemBind(runDir + "/model", "/wmsa/model", BindMode.READ_ONLY)
            .withLogConsumer(frame -> {
                System.out.print(frame.getUtf8String());
            })
            .withCopyToContainer(Transferable.of(dbProperties), "/wmsa/conf/db.properties")
            .withExposedPorts(80)
            .waitingFor(Wait.forHttp("/internal/ready").withStartupTimeout(Duration.ofMinutes(5)));

    private static GenericContainer<?> queryContainer = new GenericContainer<>(DockerImageName.parse("marginalia/query-service"))
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withNetworkAliases("query-service")
            .withNetwork(Network.SHARED)
            .withEnv(Map.of("ZOOKEEPER_HOSTS", "zookeeper:2181",
                    "SERVICE_HOST", "query-service"
            ))
            .withFileSystemBind(runDir + "/data", "/wmsa/data", BindMode.READ_ONLY)
            .withFileSystemBind(runDir + "/model", "/wmsa/model", BindMode.READ_ONLY)
            .withLogConsumer(frame -> {
                System.out.print(frame.getUtf8String());
            })
            .withCopyToContainer(Transferable.of(dbProperties), "/wmsa/conf/db.properties")
            .withExposedPorts(80)
            .waitingFor(Wait.forHttp("/internal/ready").withStartupTimeout(Duration.ofMinutes(5)));


    private static GenericContainer<?> indexContainer = new GenericContainer<>(DockerImageName.parse("marginalia/index-service"))
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withNetworkAliases("index-service")
            .withNetwork(Network.SHARED)
            .withEnv(Map.of("ZOOKEEPER_HOSTS", "zookeeper:2181",
                    "SERVICE_HOST", "index-service"
            ))
            .withFileSystemBind(runDir + "/data", "/wmsa/data", BindMode.READ_ONLY)
            .withFileSystemBind(runDir + "/model", "/wmsa/model", BindMode.READ_ONLY)
            .withLogConsumer(frame -> {
                System.out.print(frame.getUtf8String());
            })
            .withCopyToContainer(Transferable.of(dbProperties), "/wmsa/conf/db.properties")
            .withExposedPorts(80)
            .waitingFor(Wait.forHttp("/internal/started").withStartupTimeout(Duration.ofMinutes(5))); // <-- note different health check

    private static GenericContainer<?> sslContainer = new GenericContainer<>(DockerImageName.parse("marginalia/search-service-legacy"))
            .withImagePullPolicy(PullPolicy.defaultPolicy())
            .withNetworkAliases("search-service-legacy")
            .withNetwork(Network.SHARED)
            .withEnv(Map.of("ZOOKEEPER_HOSTS", "zookeeper:2181",
                                "SERVICE_HOST", "search-service-legacy"
                    ))
            .withFileSystemBind(runDir + "/data", "/wmsa/data", BindMode.READ_ONLY)
            .withFileSystemBind(runDir + "/model", "/wmsa/model", BindMode.READ_ONLY)
            .withCopyToContainer(Transferable.of(dbProperties), "/wmsa/conf/db.properties")
            .withLogConsumer(frame -> {
                System.out.print(frame.getUtf8String());
            })
            .dependsOn(controlContainer)
            .withExposedPorts(80)
            .waitingFor(Wait.forHttp("/internal/ready").withStartupTimeout(Duration.ofMinutes(5)));

    @BeforeAll
    public static void setUpAll() throws InterruptedException, SQLException {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeQuery("INSERT INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('www.marginalia.nu', 'marginalia.nu', 1)");
            stmt.executeQuery("INSERT INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('api.marginalia.nu', 'marginalia.nu', 0)");
        }

        zookeeper.start();
        controlContainer.start();
        assistantContainer.start();
        queryContainer.start();
        indexContainer.start();
        sslContainer.start();

        // Warm up the internal connection pools
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000);
            testRequest("/site/www.marginalia.nu");
            testRequest("/search?query=test");
        }
    }

    @AfterAll
    public static void tearDownAll() {
        zookeeper.stop();
        controlContainer.stop();
        assistantContainer.stop();
        indexContainer.stop();
        queryContainer.stop();
        sslContainer.stop();
    }

    @Test
    public void testStaticAsset() {
        HttpClient client = HttpClient.newHttpClient();
        String assetUrl = "http://" + sslContainer.getContainerIpAddress() + ":" + sslContainer.getMappedPort(80) + "/robots.txt";

        var req = HttpRequest.newBuilder(URI.create(assetUrl)).GET().build();
        try {
            var rsp = client.send(req, HttpResponse.BodyHandlers.discarding());
            Assertions.assertEquals(200, rsp.statusCode());
        }
        catch (Exception ex) {
            Assertions.fail("Failed to retrieve static resource", ex);
        }
    }


    @Test
    public void testFrontPage() {
        var rsp = testRequest("/");

        Assertions.assertEquals(200, rsp.statusCode(), "Unexpected status code: " + rsp.statusCode());

        String body = rsp.body();
        String contentType = rsp.headers().firstValue("Content-Type").get();

        Assertions.assertTrue(contentType.startsWith("text/html"), "Unexpected content type: " + contentType);
        Assertions.assertTrue(body.contains("Marginalia Search"), "Unexpected body: " + body);
    }

    @Test
    public void testSearch() {
        var rsp = testRequest("/search?query=test");

        Assertions.assertEquals(200, rsp.statusCode(), "Unexpected status code: " + rsp.statusCode());

        String body = rsp.body();
        String contentType = rsp.headers().firstValue("Content-Type").get();

        Assertions.assertTrue(contentType.startsWith("text/html"), "Unexpected content type: " + contentType);
        Assertions.assertTrue(body.contains("No search results found"), "Unexpected body: " + body);
    }


    @Test
    public void testCrosstalk() {
        var rsp = testRequest("/crosstalk/?domains=git.marginalia.nu,www.marginalia.nu");

        Assertions.assertEquals(200, rsp.statusCode(), "Unexpected status code: " + rsp.statusCode());

        String body = rsp.body();
        String contentType = rsp.headers().firstValue("Content-Type").get();

        Assertions.assertTrue(contentType.startsWith("text/html"), "Unexpected content type: " + contentType);
        Assertions.assertTrue(body.contains("No search results found"), "Unexpected body: " + body);
    }

    @Test
    public void testSite() {
        var rsp = testRequest("/site/www.marginalia.nu");

        Assertions.assertEquals(200, rsp.statusCode(), "Unexpected status code: " + rsp.statusCode());

        String body = rsp.body();
        String contentType = rsp.headers().firstValue("Content-Type").get();

        Assertions.assertTrue(contentType.startsWith("text/html"), "Unexpected content type: " + contentType);
        Assertions.assertTrue(body.contains("This website is not queued for crawling"), "Unexpected body: " + body);

    }

    static HttpResponse<String> testRequest(String endpoint) {
        try (HttpClient client = HttpClient.newHttpClient()) {

            var req = HttpRequest.newBuilder(URI.create("http://" + sslContainer.getContainerIpAddress() + ":" + sslContainer.getMappedPort(80) + endpoint)).GET().build();
            try {
                return client.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ex) {
                Assertions.fail("Failed to retrieve static resource", ex);
            }
        }

        Assertions.fail("Failed execute query");
        return null;  // unreacahable
    }

}
