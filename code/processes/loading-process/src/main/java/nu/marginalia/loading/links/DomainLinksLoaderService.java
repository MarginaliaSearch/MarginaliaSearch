package nu.marginalia.loading.links;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.io.processed.DomainLinkRecordParquetFileReader;
import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.loading.domains.DomainIdRegistry;
import nu.marginalia.model.processed.DomainLinkRecord;
import nu.marginalia.process.control.ProcessHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Singleton
public class DomainLinksLoaderService {

    private final HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(DomainLinksLoaderService.class);

    @Inject
    public DomainLinksLoaderService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean loadLinks(DomainIdRegistry domainIdRegistry,
                             ProcessHeartbeat heartbeat,
                             Path processedDataPathBase,
                             int untilBatch) throws IOException, SQLException {

        dropLinkData();

        try (var task = heartbeat.createAdHocTaskHeartbeat("LINKS")) {
            var linkFiles = ProcessedDataFileNames.listDomainLinkFiles(processedDataPathBase, untilBatch);

            int processed = 0;

            for (var file : linkFiles) {
                task.progress("LOAD", processed++, linkFiles.size());

                loadLinksFromFile(domainIdRegistry, file);
            }

            task.progress("LOAD", processed, linkFiles.size());
        }

        logger.info("Finished");
        return true;
    }

    private void dropLinkData() throws SQLException {
        logger.info("Truncating EC_DOMAIN_LINK");

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE EC_DOMAIN_LINK");
        }
    }

    private void loadLinksFromFile(DomainIdRegistry domainIdRegistry, Path file) throws IOException, SQLException {
        try (var domainStream = DomainLinkRecordParquetFileReader.stream(file);
             var linkLoader = new LinkLoader(domainIdRegistry))
        {
            logger.info("Loading links from {}", file);
            domainStream.forEach(linkLoader::accept);
        }
    }

    class LinkLoader implements AutoCloseable {
        private final Connection connection;
        private final PreparedStatement insertStatement;
        private final DomainIdRegistry domainIdRegistry;

        private int batchSize = 0;
        private int total = 0;

        public LinkLoader(DomainIdRegistry domainIdRegistry) throws SQLException {
            this.domainIdRegistry = domainIdRegistry;

            connection = dataSource.getConnection();
            insertStatement = connection.prepareStatement("""
                    INSERT INTO EC_DOMAIN_LINK(SOURCE_DOMAIN_ID, DEST_DOMAIN_ID)
                    VALUES (?, ?)
                    """);
        }

        void accept(DomainLinkRecord record) {
            try {
                insertStatement.setInt(1, domainIdRegistry.getDomainId(record.source));
                insertStatement.setInt(2, domainIdRegistry.getDomainId(record.dest));
                insertStatement.addBatch();
                if (++batchSize > 1000) {
                    batchSize = 0;
                    insertStatement.executeBatch();
                }
                total++;
            }
            catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void close() throws SQLException {
            if (batchSize > 0) {
                insertStatement.executeBatch();
            }

            logger.info("Inserted {} links", total);

            insertStatement.close();
            connection.close();
        }
    }
}
