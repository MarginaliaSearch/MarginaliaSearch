package nu.marginalia.svc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.service.module.ServiceConfiguration;
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

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
class DomainListRefreshServiceTest {
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
    }

    @AfterAll
    public static void shutDown() {
        dataSource.close();
    }

    @Test
    void downloadDomainsList() throws SQLException {
        DomainTypes domainTypes = new DomainTypes(dataSource);
        DomainListRefreshService service = new DomainListRefreshService(dataSource,
                domainTypes, new ServiceConfiguration(null, 1, null, -1, -1, null));

        domainTypes.updateUrlForSelection(DomainTypes.Type.CRAWL, "https://downloads.marginalia.nu/domain-list-test.txt");
        service.synchronizeDomainList();

        Map<String, Integer> result = new HashMap<>();
        try (var conn = dataSource.getConnection();
             var qs = conn.prepareStatement("""
                     SELECT DOMAIN_NAME, NODE_AFFINITY FROM EC_DOMAIN
                     """))
        {
            var rs = qs.executeQuery();
            while (rs.next()) {
                result.put(rs.getString(1), rs.getInt(2));
            }
        }
        assertEquals(
                Map.of(
                        "memex.marginalia.nu", 1,
                        "encyclopedia.marginalia.nu", 1,
                        "search.marginalia.nu", 1,
                        "www.marginalia.nu", 1
                        ),
                result);
    }
}