package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
class MigrateDomainsActorTest {

    private static final int WIDE_NODE = 2;
    private static final int OTHER_NODE = 5;

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    private MigrateDomainsActor actor;

    @BeforeAll
    static void setupDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);
    }

    @AfterAll
    static void shutDownDatabase() {
        dataSource.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        try (var conn = dataSource.getConnection();
             var clearQueue = conn.prepareStatement("DELETE FROM DOMAIN_MIGRATION_QUEUE");
             var clearRoots = conn.prepareStatement("DELETE FROM WIDE_DOMAIN_ROOTS");
             var clearDomains = conn.prepareStatement("DELETE FROM EC_DOMAIN");
             var insertDomain = conn.prepareStatement(
                     "INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES (?, ?, ?)");
             var insertRoot = conn.prepareStatement("INSERT INTO WIDE_DOMAIN_ROOTS (DOMAIN_TOP) VALUES (?)"))
        {
            clearQueue.executeUpdate();
            clearRoots.executeUpdate();
            clearDomains.executeUpdate();

            // Two subdomains of the flagged root elsewhere, one already on the wide node, and an
            // unrelated domain that must not be touched.
            insertDomain(insertDomain, "a.blogspot.com", "blogspot.com", OTHER_NODE);
            insertDomain(insertDomain, "b.blogspot.com", "blogspot.com", OTHER_NODE);
            insertDomain(insertDomain, "c.blogspot.com", "blogspot.com", WIDE_NODE);
            insertDomain(insertDomain, "example.com", "example.com", OTHER_NODE);
            insertDomain.executeBatch();

            insertRoot.setString(1, "blogspot.com");
            insertRoot.executeUpdate();
        }

        FileStorageService storageService = mock(FileStorageService.class);
        when(storageService.getOnlyActiveFileStorage(FileStorageType.CRAWL_DATA))
                .thenReturn(Optional.of(new FileStorageId(1)));

        actor = new MigrateDomainsActor(new Gson(), storageService, dataSource,
                mock(ExecutorClient.class),
                new ServiceConfiguration(null, WIDE_NODE, null, null, -1, null),
                mock(ServiceEventLog.class), mock(ServiceHeartbeat.class));
    }

    @Test
    void test() throws Exception {
        // The Initial step expands the flagged roots, then hands off to the Migrate step.
        actor.transition(new MigrateDomainsActor.Initial());

        Map<String, Integer> queued = queueByDomain();

        assertEquals(2, queued.size(), "only not-yet-owned subdomains of a flagged root are queued");
        assertEquals(WIDE_NODE, queued.get("a.blogspot.com"));
        assertEquals(WIDE_NODE, queued.get("b.blogspot.com"));
        assertNull(queued.get("c.blogspot.com"), "a subdomain already on the wide node is not re-queued");
        assertNull(queued.get("example.com"), "a domain outside the flagged root is not queued");

        actor.transition(new MigrateDomainsActor.Initial());
        assertEquals(2, queueByDomain().size());
    }

    private Map<String, Integer> queueByDomain() throws SQLException {
        Map<String, Integer> queued = new HashMap<>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT EC_DOMAIN.DOMAIN_NAME, DOMAIN_MIGRATION_QUEUE.DEST_NODE
                     FROM DOMAIN_MIGRATION_QUEUE
                     JOIN EC_DOMAIN ON EC_DOMAIN.ID = DOMAIN_MIGRATION_QUEUE.DOMAIN_ID
                     WHERE DOMAIN_MIGRATION_QUEUE.STATE = 'NEW'
                     """))
        {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                queued.put(rs.getString("DOMAIN_NAME"), rs.getInt("DEST_NODE"));
            }
        }
        return queued;
    }

    private void insertDomain(PreparedStatement insert, String domain, String topDomain, int nodeAffinity) throws SQLException {
        insert.setString(1, domain);
        insert.setString(2, topDomain);
        insert.setInt(3, nodeAffinity);
        insert.addBatch();
    }
}
