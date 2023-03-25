package nu.marginalia.loading.loader;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.SQLException;

public class SqlLoadDomainMetadata {
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SqlLoadDomainMetadata(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void load(LoaderData data, EdgeDomain domain, int knownUrls, int goodUrls, int visitedUrls) {
        int domainId = data.getDomainId(domain);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO DOMAIN_METADATA(ID,KNOWN_URLS,VISITED_URLS,GOOD_URLS) VALUES (?, ?, ?, ?)
                     """
             ))
        {
            stmt.setInt(1, domainId);
            stmt.setInt(2, knownUrls);
            stmt.setInt(3, visitedUrls);
            stmt.setInt(4, goodUrls);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            logger.warn("SQL error inserting domains", ex);

            if (getClass().desiredAssertionStatus())
                throw new RuntimeException(ex);
        }
    }
}
