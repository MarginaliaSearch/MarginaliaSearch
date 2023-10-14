package nu.marginalia.nodecfg;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.storage.FileStorageService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
            .withInitScript("db/migration/V23_11_0_005__node_config.sql")
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

        nodeConfigurationService = new NodeConfigurationService(dataSource);
    }

    @Test
    public void test() throws SQLException {
        var a = nodeConfigurationService.create("Test", false);
        var b = nodeConfigurationService.create("Foo", true);

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