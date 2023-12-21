package nu.marginalia.adjacencies;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Testcontainers
@Execution(SAME_THREAD)
@Tag("slow")
public class AdjacenciesLoaderTest {
    private static HikariDataSource dataSource;

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    @BeforeAll
    public static void setup() {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE EC_DOMAIN_NEIGHBORS_2 (
                        DOMAIN_ID INT NOT NULL,
                        NEIGHBOR_ID INT NOT NULL,
                        RELATEDNESS DOUBLE NOT NULL,
                        PRIMARY KEY (DOMAIN_ID, NEIGHBOR_ID)
                    )
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    public static void teardown() {
        dataSource.close();
        mariaDBContainer.close();
    }

    @Test
    public void testLoad() {
        var loader = new AdjacenciesLoader(dataSource);
        try {
            loader.load(new WebsiteAdjacenciesCalculator.DomainSimilarities(1,
                    List.of(new WebsiteAdjacenciesCalculator.DomainSimilarity(2, 0.5),
                            new WebsiteAdjacenciesCalculator.DomainSimilarity(3, 0.6)
                            )));
            loader.stop();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT * FROM EC_DOMAIN_NEIGHBORS_2 WHERE DOMAIN_ID=1");
        ) {
            var rs = stmt.executeQuery();
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(2, rs.getInt(2));
            Assertions.assertEquals(0.5, rs.getDouble(3));
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(3, rs.getInt(2));
            Assertions.assertEquals(0.6, rs.getDouble(3));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
