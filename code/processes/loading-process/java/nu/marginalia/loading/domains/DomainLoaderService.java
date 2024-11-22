package nu.marginalia.loading.domains;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.processed.SlopDomainLinkRecord;
import nu.marginalia.model.processed.SlopDomainRecord;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.slop.SlopTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class DomainLoaderService {

    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(DomainLoaderService.class);
    private final int nodeId;

    @Inject
    public DomainLoaderService(HikariDataSource dataSource,
                               ProcessConfiguration processConfiguration
                               ) {
        this.dataSource = dataSource;
        this.nodeId = processConfiguration.node();
    }

    enum Steps {
        PREP_DATA,
        UPDATE_AFFINITY_AND_IP,
        FETCH_ALL,
        DONE
    }

    /** Read the domain names from each input file
     *  compare with SQL domain database, fetch those
     *  that exist, insert those that don't.
     */
    public DomainIdRegistry getOrCreateDomainIds(ProcessHeartbeatImpl heartbeat, LoaderInputData inputData)
            throws IOException, SQLException
    {
        Set<String> domainNamesAll = new HashSet<>(100_000);
        DomainIdRegistry ret = new CachingDomainIdRegistry();

        try (var conn = dataSource.getConnection();
             var taskHeartbeat = heartbeat.createProcessTaskHeartbeat(Steps.class, "DOMAIN_IDS"))
        {
            taskHeartbeat.progress(Steps.PREP_DATA);

            Collection<SlopTable.Ref<SlopDomainRecord>> domainPageRefs = inputData.listDomainPages();
            Collection<SlopTable.Ref<SlopDomainLinkRecord>> domainLinkPageRefs = inputData.listDomainLinkPages();

            // Ensure that the domains we've just crawled are in the domain database to this node
            try (var inserter = new DomainInserter(conn, nodeId);
                 var processHeartbeat = heartbeat.createAdHocTaskHeartbeat("INSERT_CRAWLED_DOMAINS")) {
                // Add domain names from this data set with the current node affinity
                int pageIdx = 0;

                for (SlopTable.Ref<SlopDomainRecord> page : inputData.listDomainPages()) {
                    processHeartbeat.progress("INSERT", pageIdx++, domainPageRefs.size());

                    try (var reader = new SlopDomainRecord.DomainNameReader(page)) {
                        while (reader.hasMore()) {
                            String domainName = reader.next();
                            if (domainNamesAll.add(domainName)) {
                                inserter.accept(new EdgeDomain(domainName));
                            }
                        }
                    }
                }
            }

            // Add domains that are linked to from the domains we've just crawled, but with -1 affinity meaning they
            // can be grabbed by any index node
            try (var inserter = new DomainInserter(conn, -1);
                 var processHeartbeat = heartbeat.createAdHocTaskHeartbeat("INSERT_LINKED_DOMAINS")) {
                // Add linked domains, but with -1 affinity meaning they can be grabbed by any index node
                int pageIdx = 0;

                for (SlopTable.Ref<SlopDomainLinkRecord> page : inputData.listDomainLinkPages()) {
                    processHeartbeat.progress("INSERT", pageIdx++, domainLinkPageRefs.size());

                    try (var reader = new SlopDomainLinkRecord.Reader(page)) {
                        while (reader.hasMore()) {
                            SlopDomainLinkRecord record = reader.next();
                            String domainName = record.dest();
                            if (domainNamesAll.add(domainName)) {
                                inserter.accept(new EdgeDomain(domainName));
                            }
                        }
                    }
                }
            }

            taskHeartbeat.progress(Steps.UPDATE_AFFINITY_AND_IP);

            // Update the node affinity and IP address for each domain we have information about
            try (var processHeartbeat = heartbeat.createAdHocTaskHeartbeat("UPDATE_AFFINITY_AND_IP")) {
                // Update the node affinity and IP address for each domain
                int pageIdx = 0;

                for (SlopTable.Ref<SlopDomainRecord> page : inputData.listDomainPages()) {
                    processHeartbeat.progress("UPDATE", pageIdx++, domainPageRefs.size());

                    try (var updater = new DomainAffinityAndIpUpdater(conn, nodeId);
                         var reader = new SlopDomainRecord.DomainWithIpReader(page)
                    ) {
                        while (reader.hasMore()) {
                            var domainWithIp = reader.next();
                            updater.accept(new EdgeDomain(domainWithIp.domain()), domainWithIp.ip());
                        }
                    }
                }
            }

            taskHeartbeat.progress(Steps.FETCH_ALL);

            // Fetch the ID for all domains that we have information about
            try (var selectStmt = conn.prepareStatement("SELECT ID, LOWER(DOMAIN_NAME) FROM EC_DOMAIN")) {

                selectStmt.setFetchSize(1000);

                var rs = selectStmt.executeQuery();
                while (rs.next()) {
                    String domain = rs.getString(2);

                    if (domainNamesAll.contains(domain)) {
                        ret.add(domain, rs.getInt(1));
                    }
                }
            }

            taskHeartbeat.progress(Steps.DONE);
        }

        return ret;
    }

    public boolean loadDomainMetadata(DomainIdRegistry domainIdRegistry, ProcessHeartbeat heartbeat, LoaderInputData inputData) {

        try (var taskHeartbeat = heartbeat.createAdHocTaskHeartbeat("UPDATE-META")) {

            int processed = 0;

            Collection<SlopTable.Ref<SlopDomainRecord>> pages = inputData.listDomainPages();
            for (var page : pages) {
                taskHeartbeat.progress("UPDATE-META", processed++, pages.size());

                try (var reader = new SlopDomainRecord.Reader(page);
                     var updater = new DomainMetadataUpdater(dataSource, domainIdRegistry))
                {
                    reader.forEach(updater::accept);
                }
            }
            taskHeartbeat.progress("UPDATE-META", processed, pages.size());
        }
        catch (Exception ex) {
            logger.error("Failed inserting metadata!", ex);
        }

        return true;
    }

    private static class DomainInserter implements AutoCloseable {
        private final PreparedStatement statement;
        private final int nodeAffinity;


        private int count = 0;

        public DomainInserter(Connection connection, int affinity) throws SQLException {
            nodeAffinity = affinity;
            statement = connection.prepareStatement("INSERT IGNORE INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES (?, ?, ?)");
        }

        public void accept(EdgeDomain domain) throws SQLException {
            statement.setString(1, domain.toString());
            statement.setString(2, domain.topDomain);
            statement.setInt(3, nodeAffinity);
            statement.addBatch();

            if (++count > 1000) {
                count = 0;
                statement.executeBatch();
            }
        }

        @Override
        public void close() throws SQLException {
            if (count > 0) {
                count = 0;
                statement.executeBatch();
            }
            statement.close();
        }
    }
    private static class DomainAffinityAndIpUpdater implements AutoCloseable {
        private final PreparedStatement statement;
        private final int nodeAffinity;

        private int count = 0;

        public DomainAffinityAndIpUpdater(Connection connection, int affinity) throws SQLException {
            this.nodeAffinity = affinity;
            statement = connection.prepareStatement("""
                        UPDATE EC_DOMAIN
                            SET NODE_AFFINITY = ?, IP = ?
                            WHERE DOMAIN_NAME=?
                        """);
        }

        public void accept(EdgeDomain domain, String ip) throws SQLException {
            statement.setInt(1, nodeAffinity);
            statement.setString(2, ip);
            statement.setString(3, domain.toString());
            statement.addBatch();

            if (++count > 1000) {
                count = 0;
                statement.executeBatch();
            }
        }

        @Override
        public void close() throws SQLException {
            if (count > 0) {
                statement.executeBatch();
            }
            statement.close();
        }
    }

    private static class DomainMetadataUpdater implements AutoCloseable  {

        private final Connection connection;
        private final DomainIdRegistry idRegistry;
        private final PreparedStatement updateStatement;

        private static final Logger logger = LoggerFactory.getLogger(DomainMetadataUpdater.class);

        private int i = 0;

        private DomainMetadataUpdater(HikariDataSource dataSource, DomainIdRegistry idRegistry) throws SQLException {
            this.connection = dataSource.getConnection();
            this.idRegistry = idRegistry;
            this.updateStatement = connection.prepareStatement("""
                    REPLACE INTO DOMAIN_METADATA(ID, VISITED_URLS, GOOD_URLS, KNOWN_URLS)
                    VALUES (?, ?, ?, ?)
                    """);
        }

        public void accept(SlopDomainRecord domainRecord) {
            try {
                updateStatement.setInt(1, idRegistry.getDomainId(domainRecord.domain()));
                updateStatement.setInt(2, domainRecord.visitedUrls());
                updateStatement.setInt(3, domainRecord.goodUrls());
                updateStatement.setInt(4, domainRecord.knownUrls());
                updateStatement.addBatch();

                if (++i > 1000) {
                    updateStatement.executeBatch();
                    i = 0;
                }
            }
            catch (SQLException ex) {
                logger.error("SQL error", ex);
            }
            catch (IllegalStateException ex) {
                logger.error("ERROR", ex);
            }
        }

        @Override
        public void close() throws SQLException {
            if (i > 0) {
                updateStatement.executeBatch();
            }
            updateStatement.close();
            connection.close();
        }


    }
}
