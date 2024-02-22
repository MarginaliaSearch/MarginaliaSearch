package nu.marginalia.ranking;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.indexdomainlinks.AggregateDomainLinksClient;
import nu.marginalia.ranking.data.InvertedLinkGraphSource;
import nu.marginalia.ranking.data.LinkGraphSource;
import nu.marginalia.ranking.data.SimilarityGraphSource;
import nu.marginalia.test.TestMigrationLoader;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.mockito.Mockito;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;
import static org.mockito.Mockito.when;

@Tag("slow")
@Testcontainers
@Execution(SAME_THREAD)
public class RankingAlgorithmsContainerTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    AggregateDomainLinksClient domainLinksClient;
    AggregateDomainLinksClient.AllLinks allLinks;

    @BeforeAll
    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);

        try (var conn = dataSource.getConnection();
            var stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                INSERT INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY)
                VALUES ('memex.marginalia.nu', 'marginalia.nu', 1),
                ('search.marginalia.nu', 'marginalia.nu', 1),
                ('encyclopedia.marginalia.nu', 'marginalia.nu', 1),
                ('marginalia.nu', 'marginalia.nu', 1);    
            """);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @BeforeEach
    public void setupQueryClient() {
        domainLinksClient = Mockito.mock(AggregateDomainLinksClient.class);
        allLinks = new AggregateDomainLinksClient.AllLinks();
        when(domainLinksClient.getAllDomainLinks()).thenReturn(allLinks);

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE EC_DOMAIN_NEIGHBORS_2");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addSimilarity(int source, int dest, double similarity) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                INSERT INTO EC_DOMAIN_NEIGHBORS_2(DOMAIN_ID, NEIGHBOR_ID, RELATEDNESS)
                VALUES (?, ?, ?)
                """)) {
            stmt.setInt(1, source);
            stmt.setInt(2, dest);
            stmt.setDouble(3, similarity);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGetDomains() {
        // should all be the same, doesn't matter which one we use
        var source = new LinkGraphSource(dataSource, domainLinksClient);

        Assertions.assertEquals(List.of(1),
                source.domainIds(List.of("memex.marginalia.nu")));

        // Verify globbing
        Assertions.assertEquals(List.of(1,2,3),
                source.domainIds(List.of("%.marginalia.nu")));
    }

    @Test
    public void testLinkGraphSource() {
        allLinks.add(1, 3);

        var graph = new LinkGraphSource(dataSource, domainLinksClient).getGraph();

        Assertions.assertTrue(graph.containsVertex(1));
        Assertions.assertTrue(graph.containsVertex(2));
        Assertions.assertTrue(graph.containsVertex(3));

        Assertions.assertTrue(graph.containsEdge(1, 3));

        Assertions.assertFalse(graph.containsEdge(3, 1));
        Assertions.assertFalse(graph.containsEdge(2, 3));
        Assertions.assertFalse(graph.containsEdge(3, 2));
    }
    @Test
    public void testInvertedLinkGraphSource() {
        allLinks.add(1, 3);

        var graph = new InvertedLinkGraphSource(dataSource, domainLinksClient).getGraph();

        Assertions.assertTrue(graph.containsVertex(1));
        Assertions.assertTrue(graph.containsVertex(2));
        Assertions.assertTrue(graph.containsVertex(3));

        Assertions.assertTrue(graph.containsEdge(3, 1));

        Assertions.assertFalse(graph.containsEdge(1, 3));
        Assertions.assertFalse(graph.containsEdge(2, 3));
        Assertions.assertFalse(graph.containsEdge(3, 2));
    }
    @Test
    @SuppressWarnings("unchecked")
    public void testSimilarityGraphSource() {

        addSimilarity(1, 3, 0.5);

        var graph = (Graph<Integer, DefaultWeightedEdge>) new SimilarityGraphSource(dataSource).getGraph();

        Assertions.assertTrue(graph.containsVertex(1));
        Assertions.assertTrue(graph.containsVertex(2));
        Assertions.assertTrue(graph.containsVertex(3));

        Assertions.assertTrue(graph.containsEdge(3, 1));
        Assertions.assertTrue(graph.containsEdge(1, 3));
        Assertions.assertEquals(graph.getEdgeWeight(graph.getEdge(1, 3)), 0.5, 0.0001);

        Assertions.assertFalse(graph.containsEdge(1, 2));
        Assertions.assertFalse(graph.containsEdge(2, 3));
    }
}
