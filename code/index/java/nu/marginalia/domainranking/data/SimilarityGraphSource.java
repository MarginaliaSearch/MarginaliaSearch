package nu.marginalia.domainranking.data;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
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

    @Override
    public Graph<Integer, ?> getGraph() {
        Graph<Integer, ?> graph = new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);

        try (var conn = dataSource.getConnection()) {
            addVertices(graph);

            try (var stmt = conn.prepareStatement("""
                SELECT DOMAIN_ID, NEIGHBOR_ID, RELATEDNESS
                FROM EC_DOMAIN_NEIGHBORS_2
                """))
            {
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    int src = rs.getInt(1);
                    int dest = rs.getInt(2);

                    // Similarity data may contain domain ids that we don't have indexed,
                    // omit these from the graph.
                    if (!graph.containsVertex(src))
                        continue;
                    if (!graph.containsVertex(dest))
                        continue;

                    double weight = rs.getDouble(3);

                    graph.addEdge(src, dest);
                    graph.setEdgeWeight(src, dest, weight);
                }
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return graph;
    }
}
