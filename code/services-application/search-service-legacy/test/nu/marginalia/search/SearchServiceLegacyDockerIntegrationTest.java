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
    public static void setUpAll() throws InterruptedException {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        zookeeper.start();
        controlContainer.start();
        sslContainer.start();
    }

    @AfterAll
    public static void tearDownAll() {
        zookeeper.stop();
        controlContainer.stop();
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
        HttpClient client = HttpClient.newHttpClient();
        String assetUrl = "http://" + sslContainer.getContainerIpAddress() + ":" + sslContainer.getMappedPort(80) + "/";

        var req = HttpRequest.newBuilder(URI.create(assetUrl)).GET().build();
        try {
            var rsp = client.send(req, HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(200, rsp.statusCode());
            Assertions.assertTrue(rsp.body().contains("Marginalia Search"));
        }
        catch (Exception ex) {
            Assertions.fail("Failed to retrieve static resource", ex);
        }
    }
}
