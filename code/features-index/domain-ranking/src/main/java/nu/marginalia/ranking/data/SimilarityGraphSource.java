package nu.marginalia.ranking.data;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.sql.SQLException;

/** A source for the similarity graph, stored in EC_DOMAIN_NEIGHBORS_2,
 * which contains the cosine similarity of the incident link vectors in the link graph.
 * */
public class SimilarityGraphSource extends AbstractGraphSource {
    @Inject
    public SimilarityGraphSource(HikariDataSource dataSource) {
        super(dataSource);
    }

    /** Check if the data source is available. */
    public boolean isAvailable() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT * 
                FROM EC_DOMAIN_NEIGHBORS_2 
                LIMIT 1
                """);
             var rs = stmt.executeQuery())
        {
            return rs.next();
        }
        catch (SQLException ex) {
            return false;
        }
    }

    @SneakyThrows
    @Override
    public Graph<Integer, ?> getGraph() {
        Graph<Integer, ?> graph = new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);

        addVertices(graph);

        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement("""
                SELECT DOMAIN_ID, NEIGHBOR_ID, RELATEDNESS
                FROM EC_DOMAIN_NEIGHBORS_2
                """))
            {
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    int src = rs.getInt(1);
                    int dest = rs.getInt(2);
                    double weight = rs.getDouble(3);

                    graph.addEdge(src, dest);
                    graph.setEdgeWeight(src, dest, weight);
                }
            }
        }

        return graph;
    }
}
