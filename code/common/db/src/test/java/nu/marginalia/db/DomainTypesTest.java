package nu.marginalia.db;

import com.google.common.collect.Sets;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
            .withInitScript("db/migration/V23_07_0_001__domain_type.sql")
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

        var expectedDomains = Set.of("www.marginalia.nu", "search.marginalia.nu",
                                     "encyclopedia.marginalia.nu", "memex.marginalia.nu");

        assertEquals(4, downloadedDomains.size());
        assertEquals(Set.of(), Sets.symmetricDifference(expectedDomains, downloadedDomains));
    }

}