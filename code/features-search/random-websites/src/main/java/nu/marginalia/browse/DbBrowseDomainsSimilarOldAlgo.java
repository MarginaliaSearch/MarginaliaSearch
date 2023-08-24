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
public class DbBrowseDomainsSimilarOldAlgo {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HikariDataSource dataSource;

    @Inject
    public DbBrowseDomainsSimilarOldAlgo(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<BrowseResult> getDomainNeighborsAdjacent(int domainId, DomainBlacklist blacklist, int count) {
        final Set<BrowseResult> domains = new HashSet<>(count*3);

        final String q = """
                            SELECT EC_DOMAIN.ID AS NEIGHBOR_ID, DOMAIN_NAME, COUNT(*) AS CNT 
                            FROM EC_DOMAIN_NEIGHBORS 
                            INNER JOIN EC_DOMAIN ON NEIGHBOR_ID=EC_DOMAIN.ID 
                            INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                            INNER JOIN EC_DOMAIN_LINK ON DEST_DOMAIN_ID=EC_DOMAIN.ID 
                            WHERE 
                                STATE<2 
                            AND KNOWN_URLS<1000 
                            AND DOMAIN_ALIAS IS NULL 
                            AND EC_DOMAIN_NEIGHBORS.DOMAIN_ID = ? 
                            GROUP BY EC_DOMAIN.ID 
                            HAVING CNT < 100 
                            ORDER BY ADJ_IDX 
                            LIMIT ?
                            """;

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(q)) {
                stmt.setFetchSize(count);
                stmt.setInt(1, domainId);
                stmt.setInt(2, count);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);

                    if (!blacklist.isBlacklisted(id)) {
                        domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                    }
                }
            }

            if (domains.size() < count/2) {
                final String q2 = """
                        SELECT EC_DOMAIN.ID, DOMAIN_NAME
                        FROM EC_DOMAIN
                        INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                        INNER JOIN EC_DOMAIN_LINK B ON DEST_DOMAIN_ID=EC_DOMAIN.ID 
                        INNER JOIN EC_DOMAIN_LINK O ON O.DEST_DOMAIN_ID=EC_DOMAIN.ID
                        WHERE B.SOURCE_DOMAIN_ID=? 
                        AND STATE<2 
                        AND KNOWN_URLS<1000 
                        AND DOMAIN_ALIAS IS NULL 
                        GROUP BY EC_DOMAIN.ID 
                        HAVING COUNT(*) < 100 ORDER BY RANK ASC LIMIT ?""";
                try (var stmt = connection.prepareStatement(q2)) {

                    stmt.setFetchSize(count/2);
                    stmt.setInt(1, domainId);
                    stmt.setInt(2, count/2 - domains.size());
                    var rsp = stmt.executeQuery();
                    while (rsp.next()  && domains.size() < count/2) {
                        int id = rsp.getInt(1);
                        String domain = rsp.getString(2);

                        if (!blacklist.isBlacklisted(id)) {
                            domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                        }
                    }
                }
            }

            if (domains.size() < count/2) {
                final String q3 = """
                    SELECT EC_DOMAIN.ID, DOMAIN_NAME
                    FROM EC_DOMAIN
                    INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                    INNER JOIN EC_DOMAIN_LINK B ON B.SOURCE_DOMAIN_ID=EC_DOMAIN.ID
                    INNER JOIN EC_DOMAIN_LINK O ON O.DEST_DOMAIN_ID=EC_DOMAIN.ID
                    WHERE B.DEST_DOMAIN_ID=? 
                    AND STATE<2 
                    AND KNOWN_URLS<1000 
                    AND DOMAIN_ALIAS IS NULL 
                    GROUP BY EC_DOMAIN.ID
                    HAVING COUNT(*) < 100 
                    ORDER BY RANK ASC 
                    LIMIT ?""";
                try (var stmt = connection.prepareStatement(q3)) {
                    stmt.setFetchSize(count/2);
                    stmt.setInt(1, domainId);
                    stmt.setInt(2, count/2 - domains.size());

                    var rsp = stmt.executeQuery();
                    while (rsp.next() && domains.size() < count/2) {
                        int id = rsp.getInt(1);
                        String domain = rsp.getString(2);

                        if (!blacklist.isBlacklisted(id)) {
                            domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                        }
                    }
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }


        return new ArrayList<>(domains);
    }

    public List<BrowseResult> getRandomDomains(int count, DomainBlacklist blacklist, int set) {

        final String q = """
                SELECT DOMAIN_ID, DOMAIN_NAME
                FROM EC_RANDOM_DOMAINS
                INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID
                WHERE STATE<2
                AND DOMAIN_SET=?
                AND DOMAIN_ALIAS IS NULL
                ORDER BY RAND()
                LIMIT ?
                """;
        List<BrowseResult> domains = new ArrayList<>(count);
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement(q)) {
                stmt.setInt(1, set);;
                stmt.setInt(2, count);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);

                    if (!blacklist.isBlacklisted(id)) {
                        domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                    }
                }
            }
        }
        catch (SQLException ex) {
            logger.error("SQL error", ex);
        }
        return domains;
    }

}
