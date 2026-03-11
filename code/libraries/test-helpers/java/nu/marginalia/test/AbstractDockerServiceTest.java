package nu.marginalia.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Base class for Docker startup tests that verify a service container
 *  boots successfully end-to-end with MariaDB and Zookeeper.
 *  <p>
 *  Subclasses pass the service name to the constructor and optionally
 *  set {@code createFirstBootZnode} to false for services that create
 *  the znode themselves (control-service).
 */
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("docker")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
public abstract class AbstractDockerServiceTest {

    /** Controls whether the /first-boot znode is pre-created before
     *  starting the service container. */
    protected enum FirstBoot {
        /** Test creates the znode before starting the service (most services). */
        PRECREATE,
        /** Service creates the znode itself (control-service). */
        EXPECT_SERVICE_CREATES
    }

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AbstractDockerServiceTest.class);

    private final String serviceName;
    private final FirstBoot firstBoot;

    private Network network;
    private MariaDBContainer<?> mariadb;
    private GenericContainer<?> zookeeper;
    protected GenericContainer<?> serviceContainer;
    protected HikariDataSource dataSource;
    Path confDir;

    protected AbstractDockerServiceTest(String serviceName) {
        this(serviceName, FirstBoot.PRECREATE);
    }

    protected AbstractDockerServiceTest(String serviceName, FirstBoot firstBoot) {
        this.serviceName = serviceName;
        this.firstBoot = firstBoot;
    }

    @BeforeAll
    public void baseSetup() throws Exception {
        network = Network.newNetwork();

        mariadb = new MariaDBContainer<>("mariadb")
                .withDatabaseName("WMSA_prod")
                .withUsername("wmsa")
                .withPassword("wmsa")
                .withNetwork(network)
                .withNetworkAliases("mariadb");
        mariadb.start();

        zookeeper = new GenericContainer<>("zookeeper:3.8")
                .withExposedPorts(2181)
                .withNetwork(network)
                .withNetworkAliases("zookeeper");
        zookeeper.start();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariadb.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);

        if (firstBoot == FirstBoot.PRECREATE) {
            createZnode();
        }

        // Resolve the project root's run/ directory.
        // Jib declares /wmsa/conf, /wmsa/model, /wmsa/data as volumes,
        // so we bind each subdirectory individually to prevent
        // Docker anonymous volumes from shadowing the bind mounts.
        Path runDir = Path.of(System.getProperty("user.dir")).resolve("../../../run").normalize();

        confDir = Files.createTempDirectory("wmsa-conf");
        Files.copy(runDir.resolve("conf/db.properties"), confDir.resolve("db.properties"));
        Path propsDir = confDir.resolve("properties");
        Files.createDirectories(propsDir);
        Files.writeString(propsDir.resolve("system.properties"),
                "flyway.disable=true\n");

        serviceContainer = new GenericContainer<>("marginalia/" + serviceName + ":latest")
                .withNetwork(network)
                .withNetworkAliases(serviceName)
                .withFileSystemBind(confDir.toString(), "/wmsa/conf")
                .withFileSystemBind(runDir.resolve("model").toString(), "/wmsa/model")
                .withFileSystemBind(runDir.resolve("data").toString(), "/wmsa/data")
                .withEnv("ZOOKEEPER_HOSTS", "zookeeper:2181")
                .withEnv("JDK_JAVA_OPTIONS", "--enable-preview")
                .withExposedPorts(80)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(serviceName)))
                .withStartupTimeout(Duration.ofSeconds(60))
                .waitingFor(Wait.forHttp("/internal/ping").forPort(80).forResponsePredicate("pong"::equals));

        Instant startTime = Instant.now();
        serviceContainer.start();
        Duration startupDuration = Duration.between(startTime, Instant.now());

        logger.info("{} container started in {}.{}s",
                serviceName,
                startupDuration.toSeconds(),
                String.format("%03d", startupDuration.toMillisPart()));
    }

    @AfterAll
    public void baseTearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (serviceContainer != null) {
            serviceContainer.stop();
        }
        if (zookeeper != null) {
            zookeeper.stop();
        }
        if (mariadb != null) {
            mariadb.stop();
        }
        if (confDir != null) {
            TestUtil.clearTempDir(confDir);
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    public void pingEndpointReturnsPong() throws IOException, InterruptedException {
        var response = httpGet("/internal/ping");

        assertEquals(200, response.statusCode());
        assertEquals("pong", response.body());
    }

    @Test
    public void startedEndpointReturnsOk() throws IOException, InterruptedException {
        var response = httpGet("/internal/started");

        assertEquals(200, response.statusCode());
    }

    @Test
    public void svcStartEventLoggedInDatabase() throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM SERVICE_EVENTLOG WHERE SERVICE_BASE = ? AND EVENT_TYPE = 'SVC-START'")
        ) {
            stmt.setString(1, serviceName);
            var rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0,
                    "Expected at least one SVC-START event for " + serviceName);
        }
    }

    /** Send a GET request to the service container. */
    protected HttpResponse<String> httpGet(String path) throws IOException, InterruptedException {
        String baseUrl = "http://" + serviceContainer.getHost() + ":" + serviceContainer.getMappedPort(80);

        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Connect a Curator client to the test Zookeeper. Caller must close it. */
    protected CuratorFramework connectCurator() throws InterruptedException {
        String zkConnectString = zookeeper.getHost() + ":" + zookeeper.getMappedPort(2181);
        var curator = CuratorFrameworkFactory.newClient(
                zkConnectString,
                new ExponentialBackoffRetry(100, 3, 1000));
        curator.start();
        curator.blockUntilConnected(30, TimeUnit.SECONDS);
        return curator;
    }

    private void createZnode() throws Exception {
        try (var curator = connectCurator()) {
            curator.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath("/first-boot");
        }
    }

}
