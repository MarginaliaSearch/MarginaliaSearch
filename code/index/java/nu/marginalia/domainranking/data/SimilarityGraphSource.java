package nu.marginalia.domainranking.data;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;

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
    public DomainGraph getGraph() {
        try {
            DomainGraphBuilder builder = DomainGraphBuilder.undirectedWeighted();
            addVertices(builder);

            return builder.build(consumer -> {
                try (var conn = dataSource.getConnection();
                     var stmt = conn.prepareStatement("""
                        SELECT DOMAIN_ID, NEIGHBOR_ID, RELATEDNESS
                        FROM EC_DOMAIN_NEIGHBORS_2
                        """);
                     var rs = stmt.executeQuery())
                {
                    while (rs.next()) {
                        consumer.accept(rs.getInt(1), rs.getInt(2), rs.getDouble(3));
                    }
                }
                catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
