package nu.marginalia.browse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeIdCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;

@Singleton
public class DbBrowseDomainsFromUrlId {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HikariDataSource dataSource;

    @Inject
    public DbBrowseDomainsFromUrlId(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    private <T> String idList(EdgeIdCollection<EdgeUrl> ids) {
        StringJoiner j = new StringJoiner(",", "(", ")");
        for (var id : ids.values()) {
            j.add(Integer.toString(id));
        }
        return j.toString();
    }

    public List<BrowseResult> getBrowseResultFromUrlIds(EdgeIdCollection<EdgeUrl> urlIds) {
        if (urlIds.isEmpty())
            return Collections.emptyList();

        List<BrowseResult> ret = new ArrayList<>(urlIds.size());

        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.createStatement()) {

                String inStmt = idList(urlIds);

                var rsp = stmt.executeQuery("""
                    SELECT DOMAIN_ID, DOMAIN_NAME
                    FROM EC_URL_VIEW 
                    INNER JOIN DOMAIN_METADATA ON EC_URL_VIEW.DOMAIN_ID=DOMAIN_METADATA.ID 
                    WHERE 
                        KNOWN_URLS<5000 
                    AND QUALITY>-10 
                    AND EC_URL_VIEW.ID IN 
                    """ + inStmt); // this injection is safe, inStmt is derived from concatenating a list of integers
                while (rsp.next()) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);

                    ret.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                }
            }
        }
        catch (SQLException ex) {
            logger.error("SQL error", ex);
        }

        return ret;
    }


}
