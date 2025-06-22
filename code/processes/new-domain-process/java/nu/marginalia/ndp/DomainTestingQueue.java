package nu.marginalia.ndp;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.ndp.model.DomainToTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class DomainTestingQueue {
    private static Logger logger = LoggerFactory.getLogger(DomainTestingQueue.class);

    private final ArrayBlockingQueue<DomainToTest> queue = new ArrayBlockingQueue<>(2);

    // This will grow quite large, but should be manageable in memory, as theoretical maximum is around 100M domains,
    // order of 2 GB in memory.
    private final ConcurrentHashMap<String, Boolean> takenDomains = new ConcurrentHashMap<>();

    private final HikariDataSource dataSource;


    @Inject
    public DomainTestingQueue(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        Thread.ofPlatform()
                .name("DomainTestingQueue::fetch()")
                .start(this::fetch);
    }

    public DomainToTest next() throws InterruptedException {
        return queue.take();
    }

    public void accept(DomainToTest domain, int nodeId) {
        try (var conn = dataSource.getConnection();
             var flagOkStmt = conn.prepareStatement("""
                UPDATE NDP_NEW_DOMAINS
                SET STATE='ACCEPTED'
                WHERE DOMAIN_ID=?
                """);
             var assigNodeStmt = conn.prepareStatement("""
                UPDATE EC_DOMAIN SET NODE_AFFINITY=?
                WHERE ID=?
                """)
             )
        {
            conn.setAutoCommit(false);
            flagOkStmt.setInt(1, domain.domainId());
            flagOkStmt.executeUpdate();

            assigNodeStmt.setInt(1, nodeId);
            assigNodeStmt.setInt(2, domain.domainId());
            assigNodeStmt.executeUpdate();
            conn.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to accept domain in database", e);
        }
    }

    public void reject(DomainToTest domain) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                UPDATE NDP_NEW_DOMAINS
                SET STATE='REJECTED', CHECK_COUNT=CHECK_COUNT + 1
                WHERE DOMAIN_ID=?
                """))
        {
            conn.setAutoCommit(false);
            stmt.setInt(1, domain.domainId());
            stmt.executeUpdate();
            conn.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to reject domain in database", e);
        }
    }

    public void fetch() {
        while (true) {
            List<DomainToTest> domains = new ArrayList<>(2000);
            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement("""
                    SELECT DOMAIN_ID, DOMAIN_NAME
                    FROM NDP_NEW_DOMAINS
                    INNER JOIN EC_DOMAIN ON ID=DOMAIN_ID
                    WHERE NDP_NEW_DOMAINS.STATE = 'NEW'
                    ORDER BY PRIORITY DESC
                    LIMIT 2000
                    """))
            {
                var rs = stmt.executeQuery();

                while (rs.next()) {
                    int domainId = rs.getInt("DOMAIN_ID");
                    String domainName = rs.getString("DOMAIN_NAME");
                    if (takenDomains.put(domainName, true) != null) {
                        logger.warn("Domain {} is already processed, skipping", domainName);
                        continue; // Skip if already taken
                    }
                    domains.add(new DomainToTest(domainName, domainId));
                }

                if (domains.isEmpty()) {
                    refreshQueue(conn);
                }
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to fetch domains from database", e);
            }

            try {
                for (var domain : domains) {
                    queue.put(domain);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Domain fetching interrupted", e);
            }
        }
    }

    private void refreshQueue(Connection conn) {
        logger.info("Refreshing domain queue in database");
        try (var stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            logger.info("Revitalizing rejected domains");

            // Revitalize rejected domains
            stmt.executeUpdate("""
                UPDATE NDP_NEW_DOMAINS
                SET STATE='NEW'
                WHERE NDP_NEW_DOMAINS.STATE = 'REJECTED'
                AND DATE_ADD(TS_CHANGE, INTERVAL CHECK_COUNT DAY) > NOW()
                """);
            conn.commit();

            logger.info("Queue refreshed successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh queue in database", e);
        }
    }

}
