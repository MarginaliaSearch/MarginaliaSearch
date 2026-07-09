package nu.marginalia.ndp;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
class DomainNodeAllocatorTest {

    private static final int BATCH_NODE = 1;
    private static final int WIDE_NODE = 2;

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    @BeforeAll
    static void setup() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);

        var nodeConfigurationService = new NodeConfigurationService(dataSource);
        nodeConfigurationService.create(BATCH_NODE, "batch", true, false, NodeProfile.BATCH_CRAWL);
        nodeConfigurationService.create(WIDE_NODE, "wide", false, false, NodeProfile.WIDE_DOMAINS);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("INSERT INTO WIDE_DOMAIN_ROOTS (DOMAIN_TOP) VALUES ('blogspot.com')")) {
            stmt.executeUpdate();
        }
    }

    @AfterAll
    static void shutDown() {
        dataSource.close();
    }

    @Test
    void test() {
        var allocator = new DomainNodeAllocator(new NodeConfigurationService(dataSource), dataSource);

        // Subdomains of the flagged root go to the wide node
        assertEquals(WIDE_NODE, allocator.nextNodeId("foo.blogspot.com"));
        assertEquals(WIDE_NODE, allocator.nextNodeId("bar.blogspot.com"));

        // Everything else goes to the only viable batch node
        assertEquals(BATCH_NODE, allocator.nextNodeId("example.com"));
        assertEquals(BATCH_NODE, allocator.nextNodeId("another.example.com"));
    }
}
