package nu.marginalia.search.svc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

@Tag("slow")
@Testcontainers
class SearchAddToCrawlQueueServiceTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    private DbDomainQueries domainQueries;
    private SearchAddToCrawlQueueService addToCrawlQueueService;

    @BeforeEach
    public void setUp() throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeQuery("DELETE FROM EC_DOMAIN"); // Wipe any old state from other test runs

            stmt.executeQuery("INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('known.example.com', 'example.com', -1)");
            stmt.executeQuery("INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('added.example.com', 'example.com', 0)");
            stmt.executeQuery("INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES ('indexed.example.com', 'example.com', 1)");
        }

        domainQueries = new DbDomainQueries(dataSource);
        addToCrawlQueueService = new SearchAddToCrawlQueueService(domainQueries, dataSource);
    }

    @BeforeAll
    public static void setUpAll() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);
    }

    private int getNodeAffinity(String domainName) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT NODE_AFFINITY FROM EC_DOMAIN WHERE DOMAIN_NAME=?"))
        {
            stmt.setString(1, domainName);
            var rsp = stmt.executeQuery();
            if (rsp.next()) {
                return rsp.getInt(1);
            }
        }

        return -1;
    }

    @Test
    void addToCrawlQueue() throws SQLException {
        int knownId = domainQueries.getDomainId(new EdgeDomain("known.example.com"));
        int addedId = domainQueries.getDomainId(new EdgeDomain("added.example.com"));
        int indexedId = domainQueries.getDomainId(new EdgeDomain("indexed.example.com"));

        addToCrawlQueueService.addToCrawlQueue(knownId);
        addToCrawlQueueService.addToCrawlQueue(addedId);
        addToCrawlQueueService.addToCrawlQueue(indexedId);

        Assertions.assertEquals(0, getNodeAffinity("known.example.com"));
        Assertions.assertEquals(0, getNodeAffinity("added.example.com"));
        Assertions.assertEquals(1, getNodeAffinity("indexed.example.com"));
    }

}