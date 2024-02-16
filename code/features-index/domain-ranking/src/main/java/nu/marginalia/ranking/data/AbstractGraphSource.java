package nu.marginalia.ranking.data;

import com.zaxxer.hikari.HikariDataSource;
import org.jgrapht.Graph;

import java.sql.SQLException;
import java.util.*;

public abstract class AbstractGraphSource implements GraphSource {
    protected final HikariDataSource dataSource;

    protected AbstractGraphSource(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public abstract Graph<Integer, ?> getGraph();

    /** Adds all indexed domain ids as vertices to the graph. */
    protected void addVertices(Graph<Integer, ?> graph) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT ID 
                FROM EC_DOMAIN 
                WHERE NODE_AFFINITY > 0
                """);
             var rs = stmt.executeQuery())
        {
            while (rs.next()) {
                graph.addVertex(rs.getInt(1));
            }
        }
    }

    @Override
    public List<Integer> domainIds(List<String> domainNameList) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT ID 
                FROM EC_DOMAIN 
                WHERE DOMAIN_NAME LIKE ?
                """))
        {
            Set<Integer> retSet = new HashSet<>();

            for (String domainName : domainNameList) {
                stmt.setString(1, domainName);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        retSet.add(rs.getInt(1));
                    }
                }
            }

            var ret = new ArrayList<>(retSet);
            ret.sort(Comparator.naturalOrder());
            return ret;
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
