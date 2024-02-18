package nu.marginalia.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Tag("slow")
class DomainRankingSetsServiceTest {

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    @BeforeAll
    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        // The migration SQL will insert a few default values, we want to remove them
        wipeDomainRankingSets(dataSource);
    }

    @AfterEach
    public void tearDown() {
        wipeDomainRankingSets(dataSource);
    }

    @AfterAll
    static void tearDownAll() {
        dataSource.close();
        mariaDBContainer.close();
    }

    @Test
    public void testScenarios() throws Exception {
        var service = new DomainRankingSetsService(dataSource);

        var newValue = new DomainRankingSetsService.DomainRankingSet(
                "test",
                "Test domain set",
                10,
                "test\\.nu"
        );
        var newValue2 = new DomainRankingSetsService.DomainRankingSet(
                "test2",
                "Test domain set 2",
                20,
                "test\\.nu 2"
        );
        service.upsert(newValue);
        service.upsert(newValue2);
        assertEquals(newValue, service.get("test").orElseThrow());

        var allValues = service.getAll();
        assertEquals(2, allValues.size());
        assertTrue(allValues.contains(newValue));
        assertTrue(allValues.contains(newValue2));

        service.delete(newValue);
        assertFalse(service.get("test").isPresent());

        service.delete(newValue2);
        assertFalse(service.get("test2").isPresent());

        allValues = service.getAll();
        assertEquals(0, allValues.size());
    }

    private static void wipeDomainRankingSets(HikariDataSource dataSource) {
        var service = new DomainRankingSetsService(dataSource);
        service.getAll().forEach(service::delete);
    }
}