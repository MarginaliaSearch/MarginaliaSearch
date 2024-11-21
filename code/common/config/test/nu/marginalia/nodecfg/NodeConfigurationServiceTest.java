package nu.marginalia.nodecfg;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
public class NodeConfigurationServiceTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static NodeConfigurationService nodeConfigurationService;

    @BeforeAll
    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        nodeConfigurationService = new NodeConfigurationService(dataSource);
    }

    @Test
    public void test() throws SQLException {
        var a = nodeConfigurationService.create(1, "Test", false, false, NodeProfile.MIXED);
        var b = nodeConfigurationService.create(2, "Foo", true, false, NodeProfile.MIXED);

        assertEquals(1, a.node());
        assertEquals("Test", a.description());
        assertFalse(a.acceptQueries());

        assertEquals(2, b.node());
        assertEquals("Foo", b.description());
        assertTrue(b.acceptQueries());

        var list = nodeConfigurationService.getAll();
        assertEquals(2, list.size());
        assertEquals(a, list.get(0));
        assertEquals(b, list.get(1));

    }
}