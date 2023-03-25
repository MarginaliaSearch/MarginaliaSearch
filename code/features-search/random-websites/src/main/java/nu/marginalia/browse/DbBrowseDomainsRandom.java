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
public class DbBrowseDomainsRandom {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HikariDataSource dataSource;

    @Inject
    public DbBrowseDomainsRandom(HikariDataSource dataSource) {
        this.dataSource = dataSource;
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
