package nu.marginalia.loading.domains;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.io.processed.DomainLinkRecordParquetFileReader;
import nu.marginalia.io.processed.DomainRecordParquetFileReader;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.model.EdgeDomain;
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
        Set<String> domainNamesAll = new HashSet<>();
        DomainIdRegistry ret = new DomainIdRegistry();

        try (var conn = dataSource.getConnection();
             var selectStmt = conn.prepareStatement("""
                     SELECT ID, DOMAIN_NAME FROM EC_DOMAIN WHERE DOMAIN_NAME=?
                     """)
        ) {

            try (var inserter = new DomainInserter(conn, nodeId)) {
                for (var domain : readSetDomainNames(inputData)) {
                    inserter.accept(new EdgeDomain(domain));
                    domainNamesAll.add(domain);
                }
            }
            try (var inserter = new DomainInserter(conn, -1)) {
                for (var domain : readReferencedDomainNames(inputData)) {
                    inserter.accept(new EdgeDomain(domain));
                    domainNamesAll.add(domain);
                }
            }

            try (var updater = new DomainAffinityUpdater(conn, nodeId)) {
                for (var domain : readSetDomainNames(inputData)) {
                    updater.accept(new EdgeDomain(domain));
                }
            }

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

    Collection<String> readSetDomainNames(LoaderInputData inputData) throws IOException {
        final Set<String> domainNamesAll = new HashSet<>(100_000);

        var domainFiles = inputData.listDomainFiles();
        for (var file : domainFiles) {
            domainNamesAll.addAll(DomainRecordParquetFileReader.getDomainNames(file));
        }

        return domainNamesAll;
    }

    Collection<String> readReferencedDomainNames(LoaderInputData inputData) throws IOException {
        final Set<String> domainNamesAll = new HashSet<>(100_000);

        var linkFiles = inputData.listDomainLinkFiles();
        for (var file : linkFiles) {
            domainNamesAll.addAll(DomainLinkRecordParquetFileReader.getDestDomainNames(file));
        }

        return domainNamesAll;
    }

    private class DomainInserter implements AutoCloseable {
        private final PreparedStatement statement;
        private final int nodeAffinity;


        private int count = 0;

        public DomainInserter(Connection connection, int affinity) throws SQLException {
            nodeAffinity = affinity;
            statement = connection.prepareStatement("INSERT IGNORE INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES (?, ?, ?)");
        }

        public void accept(EdgeDomain domain) throws SQLException {
            statement.setString(1, domain.toString());
            statement.setString(2, domain.domain);
            statement.setInt(3, nodeAffinity);
            statement.addBatch();

            if (++count > 1000) {
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
    private static class DomainAffinityUpdater implements AutoCloseable {
        private final PreparedStatement statement;
        private final int nodeAffinity;

        private int count = 0;

        public DomainAffinityUpdater(Connection connection, int affinity) throws SQLException {
            this.nodeAffinity = affinity;
            statement = connection.prepareStatement("UPDATE EC_DOMAIN SET NODE_AFFINITY = ? WHERE DOMAIN_NAME=?");
        }

        public void accept(EdgeDomain domain) throws SQLException {
            statement.setInt(1, nodeAffinity);
            statement.setString(2, domain.toString());
            statement.addBatch();

            if (++count > 1000) {
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
}
