package nu.marginalia.nodecfg;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.nodecfg.model.NodeConfiguration;
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


    // Test all the fields that are only exposed via save()
    @Test
    public void testSaveChanges() throws SQLException {
        var original = nodeConfigurationService.create(1, "Test", false, false, NodeProfile.MIXED);

        assertEquals(1, original.node());
        assertEquals("Test", original.description());
        assertFalse(original.acceptQueries());

        var precession = new NodeConfiguration(
                original.node(),
                "Foo",
                true,
                original.autoClean(),
                original.includeInPrecession(),
                !original.autoAssignDomains(),
                original.keepWarcs(),
                original.profile(),
                original.disabled()
        );

        nodeConfigurationService.save(precession);
        precession = nodeConfigurationService.get(original.node());
        assertNotEquals(original.autoAssignDomains(), precession.autoAssignDomains());

        var autoClean = new NodeConfiguration(
                original.node(),
                "Foo",
                true,
                !original.autoClean(),
                original.includeInPrecession(),
                original.autoAssignDomains(),
                original.keepWarcs(),
                original.profile(),
                original.disabled()
        );

        nodeConfigurationService.save(autoClean);
        autoClean = nodeConfigurationService.get(original.node());
        assertNotEquals(original.autoClean(), autoClean.autoClean());

        var disabled = new NodeConfiguration(
                original.node(),
                "Foo",
                true,
                autoClean.autoClean(),
                autoClean.includeInPrecession(),
                autoClean.autoAssignDomains(),
                autoClean.keepWarcs(),
                autoClean.profile(),
                !autoClean.disabled()
        );
        nodeConfigurationService.save(disabled);
        disabled = nodeConfigurationService.get(original.node());
        assertNotEquals(autoClean.disabled(), disabled.disabled());
    }
}