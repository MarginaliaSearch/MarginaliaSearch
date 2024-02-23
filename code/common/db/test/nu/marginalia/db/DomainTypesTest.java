package nu.marginalia.db;

import com.google.common.collect.Sets;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("slow")
@Testcontainers
public class DomainTypesTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static DomainTypes domainTypes;

    @BeforeAll
    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);

        domainTypes = new DomainTypes(dataSource);
    }

    @AfterAll
    public static void teardown() {
        dataSource.close();
    }

    @Test
    public void reloadDomainsList() throws SQLException, IOException {
        domainTypes.reloadDomainsList(DomainTypes.Type.TEST);

        var downloadedDomains = new HashSet<>(domainTypes.getAllDomainsByType(DomainTypes.Type.TEST));

        var expectedDomains = Set.of("www.marginalia.nu", "search.marginalia.nu", "docs.marginalia.nu",
                                     "encyclopedia.marginalia.nu", "memex.marginalia.nu");

        assertEquals(expectedDomains.size(), downloadedDomains.size());
        assertEquals(Set.of(), Sets.symmetricDifference(expectedDomains, downloadedDomains));
    }

    @Test
    public void configure() throws SQLException {
        assertEquals("", domainTypes.getUrlForSelection(DomainTypes.Type.CRAWL));
        domainTypes.updateUrlForSelection(DomainTypes.Type.CRAWL, "test");
        assertEquals("test", domainTypes.getUrlForSelection(DomainTypes.Type.CRAWL));
    }

}