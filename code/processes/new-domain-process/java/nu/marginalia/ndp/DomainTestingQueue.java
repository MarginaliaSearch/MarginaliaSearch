package nu.marginalia.ndp;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import nu.marginalia.api.linkgraph.AggregateLinkGraphClient;
import nu.marginalia.ndp.model.DomainToTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Duration;
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
    private final AggregateLinkGraphClient linkGraphClient;


    @Inject
    public DomainTestingQueue(HikariDataSource dataSource,
                              AggregateLinkGraphClient linkGraphClient
                              ) {
        this.dataSource = dataSource;
        this.linkGraphClient = linkGraphClient;


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
             var assignNodeStmt = conn.prepareStatement("""
                UPDATE EC_DOMAIN SET NODE_AFFINITY=?
                WHERE ID=?
                AND EC_DOMAIN.NODE_AFFINITY < 0
                """)
             )
        {
            conn.setAutoCommit(false);
            flagOkStmt.setInt(1, domain.domainId());
            flagOkStmt.executeUpdate();

            assignNodeStmt.setInt(1, nodeId);
            assignNodeStmt.setInt(2, domain.domainId());
            assignNodeStmt.executeUpdate();
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
        try (var conn = dataSource.getConnection()) {
            refreshQueue(conn);
        } catch (Exception e) {
            logger.error("Error refreshing the ndp queue");
            throw new RuntimeException(e);
        }

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
                    if (!refreshQueue(conn)) {
                        throw new RuntimeException("No new domains found, aborting!");
                    }
                }
            }
            catch (RuntimeException e) {
                throw e; // Rethrow runtime exceptions to avoid wrapping them in another runtime exception
            }
            catch (Exception e) {
                logger.error("Error in ndp process");
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

    private boolean refreshQueue(Connection conn) {
        logger.info("Refreshing domain queue in database");

        Int2IntMap domainIdToCount = new Int2IntOpenHashMap();

        // Load known domain IDs from the database to avoid inserting duplicates from NDP_NEW_DOMAINS
        // or domains that are already assigned to a node
        {
            IntOpenHashSet knownIds = new IntOpenHashSet();

            try (var stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT DOMAIN_ID FROM NDP_NEW_DOMAINS");
                rs.setFetchSize(10_000);
                while (rs.next()) {
                    int domainId = rs.getInt("DOMAIN_ID");
                    knownIds.add(domainId);
                }

                rs = stmt.executeQuery("SELECT ID FROM EC_DOMAIN WHERE NODE_AFFINITY>=0");
                rs.setFetchSize(10_000);
                while (rs.next()) {
                    int domainId = rs.getInt("ID");
                    knownIds.add(domainId);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load known domain IDs from database", e);
            }

            // Ensure the link graph is ready before proceeding.  This is mainly necessary in a cold reboot
            // of the entire system.
            try {
                logger.info("Waiting for link graph client to be ready...");
                linkGraphClient.waitReady(Duration.ofHours(1));
                logger.info("Link graph client is ready, fetching domain links...");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Fetch all domain links from the link graph and count by how many sources each dest domain is linked from
            var iter = linkGraphClient.getAllDomainLinks().iterator();
            while (iter.advance()) {
                int dest = iter.dest();
                if (!knownIds.contains(dest)) {
                    domainIdToCount.mergeInt(dest, 1, (i, j) -> i + j);
                }
            }
        }

        boolean didInsert = false;

        /* Insert new domains into NDP_NEW_DOMAINS table */
        try (var insertStmt = conn.prepareStatement("""
                INSERT INTO NDP_NEW_DOMAINS (DOMAIN_ID, PRIORITY) VALUES (?, ?)
                       ON CONFLICT(DOMAIN_ID) DO UPDATE SET PRIORITY = excluded.PRIORITY
                """)) {
            conn.setAutoCommit(false);

            int cnt = 0;
            for (var entry : domainIdToCount.int2IntEntrySet()) {
                int domainId = entry.getIntKey();
                int count = entry.getIntValue();

                insertStmt.setInt(1, domainId);
                insertStmt.setInt(2, count);
                insertStmt.addBatch();

                if (++cnt >= 1000) {
                    cnt = 0;
                    insertStmt.executeBatch(); // Execute in batches to avoid memory issues
                    conn.commit();
                    didInsert = true;
                }
            }
            if (cnt != 0) {
                insertStmt.executeBatch(); // Execute any remaining batch
                conn.commit();
                didInsert = true;
            }

            logger.info("Queue refreshed successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh queue in database", e);
        }

        // Clean up NDP_NEW_DOMAINS table to remove any domains that are already in EC_DOMAIN
        // This acts not only to clean up domains that we've flagged as ACCEPTED, but also to
        // repair inconsistent states where domains might have incorrectly been added to NDP_NEW_DOMAINS
        try (var stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM NDP_NEW_DOMAINS WHERE DOMAIN_ID IN (SELECT ID FROM EC_DOMAIN WHERE NODE_AFFINITY>=0)");
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to clean up NDP_NEW_DOMAINS", e);
        }

        return didInsert;
    }

}
