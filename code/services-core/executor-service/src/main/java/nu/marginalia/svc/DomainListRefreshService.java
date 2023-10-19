package nu.marginalia.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DomainListRefreshService {

    private final HikariDataSource dataSource;
    private final DomainTypes domainTypes;
    private final int nodeId;

    private static final Logger logger = LoggerFactory.getLogger(DomainListRefreshService.class);

    @Inject
    public DomainListRefreshService(HikariDataSource dataSource,
                                    DomainTypes domainTypes,
                                    ServiceConfiguration serviceConfiguration
                                ) {
        this.dataSource = dataSource;
        this.domainTypes = domainTypes;
        this.nodeId = serviceConfiguration.node();
    }

    /** Downloads URLs from the file specified in DomainType.CRAWL and inserts them
     * into the domain table, assigning them to the current partition
      */
    public void synchronizeDomainList() {
        try (var conn = dataSource.getConnection();
             var insert = conn.prepareStatement("""
                     INSERT IGNORE INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY)
                     VALUES (?, ?, ?)
                     """);
             var update = conn.prepareStatement("""
                     UPDATE EC_DOMAIN SET NODE_AFFINITY=? WHERE DOMAIN_NAME=? AND NODE_AFFINITY <= 0
                     """)

        ){
            List<String> domainsAll = new ArrayList<>();
            domainsAll.addAll(getCrawlQueue(conn));
            domainsAll.addAll(domainTypes.downloadList(DomainTypes.Type.CRAWL));

            // Case 1: The domains are in the table, but have no affinity defined
            for (var domain : domainsAll) {
                update.setString(1, domain.toLowerCase());
                update.setInt(2, nodeId);
                update.addBatch();
            }
            update.executeBatch();


            // Case 2: The domains are missing form the table
            for (var domain : domainsAll) {
                var parsed = new EdgeDomain(domain);
                insert.setString(1, domain.toLowerCase());
                insert.setString(2, parsed.domain);
                insert.setInt(3, nodeId);
                insert.addBatch();
            }
            insert.executeBatch();

            cleanCrawlQueue(conn);
        }
        catch (Exception ex) {
            logger.warn("Failed to insert domains", ex);
        }
    }

    private List<String> getCrawlQueue(Connection connection) {
        List<String> ret = new ArrayList<>();
        try (var q = connection.prepareStatement("SELECT DOMAIN_NAME FROM CRAWL_QUEUE")) {
            var rs = q.executeQuery();
            while (rs.next()) {
                ret.add(rs.getString(1));
            }
        }
        catch (Exception ex) {
            logger.warn("Failed to fetch from crawl queue", ex);
        }

        return ret;
    }

    private void cleanCrawlQueue(Connection connection) {
        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    DELETE CRAWL_QUEUE
                    FROM CRAWL_QUEUE INNER JOIN EC_DOMAIN ON CRAWL_QUEUE.DOMAIN_NAME=EC_DOMAIN.DOMAIN_NAME
                    WHERE NODE_AFFINITY>0
                    """);
        } catch (SQLException e) {
            logger.warn("Failed to clean up crawl queue", e);
        }
    }

}
