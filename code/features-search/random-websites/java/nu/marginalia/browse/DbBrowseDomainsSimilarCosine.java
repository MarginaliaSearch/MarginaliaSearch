package nu.marginalia.browse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.db.DomainBlacklist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;

@Singleton
public class DbBrowseDomainsSimilarCosine {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HikariDataSource dataSource;

    @Inject
    public DbBrowseDomainsSimilarCosine(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<BrowseResult> getDomainNeighborsAdjacentCosineRequireScreenshot(int domainId, DomainBlacklist blacklist, int count) {
        List<BrowseResult> domains = new ArrayList<>(count);

        String q = """
                        SELECT
                            EC_DOMAIN.ID,
                            NV.NEIGHBOR_NAME,
                            NV.RELATEDNESS,
                            EC_DOMAIN.INDEXED
                        FROM EC_NEIGHBORS_VIEW NV
                        INNER JOIN DATA_DOMAIN_SCREENSHOT ON DATA_DOMAIN_SCREENSHOT.DOMAIN_NAME=NV.NEIGHBOR_NAME
                        INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=NV.NEIGHBOR_ID
                        WHERE NV.DOMAIN_ID=?
                        GROUP BY NV.NEIGHBOR_ID
                        ORDER BY NV.RELATEDNESS DESC
                        """;

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(q)) {
                stmt.setFetchSize(count);
                stmt.setInt(1, domainId);
                stmt.setInt(2, count);
                var rsp = stmt.executeQuery();
                while (rsp.next() && domains.size() < count) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);
                    double relatedness = rsp.getDouble(3);
                    boolean indexed = rsp.getBoolean("INDEXED");

                    if (!blacklist.isBlacklisted(id)) {
                        domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, relatedness, indexed));
                    }
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return domains;
    }

}
