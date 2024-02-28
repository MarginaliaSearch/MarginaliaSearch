package nu.marginalia.loading.domains;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.io.processed.DomainLinkRecordParquetFileReader;
import nu.marginalia.io.processed.DomainRecordParquetFileReader;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.processed.DomainRecord;
import nu.marginalia.model.processed.DomainWithIp;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

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

    /** Read the domain names from each parquet file
     *  compare with SQL domain database, fetch those
     *  that exist, insert those that don't.
     */
    public DomainIdRegistry getOrCreateDomainIds(LoaderInputData inputData)
            throws IOException, SQLException
    {
        Set<String> domainNamesAll = new HashSet<>(100_000);
        DomainIdRegistry ret = new DomainIdRegistry();

        try (var conn = dataSource.getConnection();
             var selectStmt = conn.prepareStatement("""
                     SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?
                     """)
        ) {

            try (var inserter = new DomainInserter(conn, nodeId)) {
                for (var domainWithIp : readBasicDomainInformation(inputData)) {
                    inserter.accept(new EdgeDomain(domainWithIp.domain));
                    domainNamesAll.add(domainWithIp.domain);
                }
            }
            try (var inserter = new DomainInserter(conn, -1)) {
                for (var domain : readReferencedDomainNames(inputData)) {
                    inserter.accept(new EdgeDomain(domain));
                    domainNamesAll.add(domain);
                }
            }

            try (var updater = new DomainAffinityAndIpUpdater(conn, nodeId)) {
                for (var domainWithIp : readBasicDomainInformation(inputData)) {
                    updater.accept(new EdgeDomain(domainWithIp.domain), domainWithIp.ip);
                }
            }

            selectStmt.setFetchSize(1000);
            for (var domain : domainNamesAll) {
                selectStmt.setString(1, domain);
                var rs = selectStmt.executeQuery();
                if (rs.next()) {
                    ret.add(domain, rs.getInt(1));
                }
                else {
                    logger.error("Unknown domain {}", domain);
                }
            }
        }

        return ret;
    }

    Collection<DomainWithIp> readBasicDomainInformation(LoaderInputData inputData) throws IOException {
        final Set<DomainWithIp> domainsAll = new HashSet<>(100_000);

        var domainFiles = inputData.listDomainFiles();
        for (var file : domainFiles) {
            domainsAll.addAll(DomainRecordParquetFileReader.getBasicDomainInformation(file));
        }

        return domainsAll;
    }

    Collection<String> readReferencedDomainNames(LoaderInputData inputData) throws IOException {
        final Set<String> domainNamesAll = new HashSet<>(100_000);

        var linkFiles = inputData.listDomainLinkFiles();
        for (var file : linkFiles) {
            domainNamesAll.addAll(DomainLinkRecordParquetFileReader.getDestDomainNames(file));
        }

        return domainNamesAll;
    }

    public boolean loadDomainMetadata(DomainIdRegistry domainIdRegistry, ProcessHeartbeatImpl heartbeat, LoaderInputData inputData) {

        var files = inputData.listDomainFiles();

        try (var taskHeartbeat = heartbeat.createAdHocTaskHeartbeat("UPDATE-META")) {

            int processed = 0;

            for (var file : files) {
                taskHeartbeat.progress("UPDATE-META", processed++, files.size());

                try (var stream = DomainRecordParquetFileReader.stream(file);
                     var updater = new DomainMetadataUpdater(dataSource, domainIdRegistry)
                ) {
                    stream.forEach(updater::accept);
                }
            }
            taskHeartbeat.progress("UPDATE-META", processed, files.size());
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

        public void accept(DomainRecord domainRecord) {
            try {
                updateStatement.setInt(1, idRegistry.getDomainId(domainRecord.domain));
                updateStatement.setInt(2, domainRecord.visitedUrls);
                updateStatement.setInt(3, domainRecord.goodUrls);
                updateStatement.setInt(4, domainRecord.knownUrls);
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
