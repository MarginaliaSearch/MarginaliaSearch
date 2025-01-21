package nu.marginalia.index.results;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
class DomainRankingOverridesTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    private static DbDomainQueries domainQueries;

    @BeforeAll
    public static void setup() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        var dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeQuery("DELETE FROM EC_DOMAIN"); // Wipe any old state from other test runs

            stmt.executeQuery("INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('first.example.com', 'example.com', 1)");
            stmt.executeQuery("INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('second.example.com', 'example.com', 1)");
            stmt.executeQuery("INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('third.example.com', 'example.com', 1)");
            stmt.executeQuery("INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('not-added.example.com', 'example.com', 1)");
        }

        domainQueries = new DbDomainQueries(dataSource);

    }

    @Test
    public void test() throws IOException {

        Path overridesFile = Files.createTempFile(getClass().getSimpleName(), ".txt");
        try {

            Files.writeString(overridesFile, """
                    # A comment
                    value 0.75
                    domain first.example.com
                    domain second.example.com
                    
                    value 1.1
                    domain third.example.com
                    """,
                    StandardOpenOption.APPEND);

            var overrides = new DomainRankingOverrides(domainQueries, overridesFile);

            overrides.reloadFile();

            Assertions.assertEquals(0.75, overrides.getRankingFactor(
                    domainQueries.getDomainId(new EdgeDomain("first.example.com"))
            ));
            Assertions.assertEquals(0.75, overrides.getRankingFactor(
                    domainQueries.getDomainId(new EdgeDomain("second.example.com"))
            ));
            Assertions.assertEquals(1.1, overrides.getRankingFactor(
                    domainQueries.getDomainId(new EdgeDomain("third.example.com"))
            ));
            Assertions.assertEquals(1.0, overrides.getRankingFactor(
                    domainQueries.getDomainId(new EdgeDomain("not-added.example.com"))
            ));
            Assertions.assertEquals(1.0, overrides.getRankingFactor(1<<23));

        }
        finally {
            Files.deleteIfExists(overridesFile);
        }
    }

}