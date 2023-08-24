package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RandomExplorationService {

    private final HikariDataSource dataSource;

    @Inject
    public RandomExplorationService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void removeRandomDomains(int[] ids) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM EC_RANDOM_DOMAINS
                     WHERE DOMAIN_ID = ?
                     AND DOMAIN_SET = 0
                     """))
        {
            for (var id : ids) {
                stmt.setInt(1, id);
                stmt.addBatch();
            }
            stmt.executeBatch();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        }
    }

    public List<RandomDomainResult> getDomains(int afterId, int numResults) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT DOMAIN_ID, DOMAIN_NAME FROM EC_RANDOM_DOMAINS
                     INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID
                     WHERE DOMAIN_ID >= ?
                     LIMIT ?
                     """))
        {
            List<RandomDomainResult> ret = new ArrayList<>(numResults);
            stmt.setInt(1, afterId);
            stmt.setInt(2, numResults);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                ret.add(new RandomDomainResult(rs.getInt(1), rs.getString(2)));
            }
            return ret;
        }
    }


    public record RandomDomainResult(int id, String domainName) {}
}
