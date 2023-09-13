package nu.marginalia.loading.domains;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.io.processed.DomainLinkRecordParquetFileReader;
import nu.marginalia.io.processed.DomainRecordParquetFileReader;
import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

@Singleton
public class DomainLoaderService {

    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(DomainLoaderService.class);

    @Inject
    public DomainLoaderService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Read the domain names from each parquet file
     *  compare with SQL domain database, fetch those
     *  that exist, insert those that don't.
     */
    public DomainIdRegistry getOrCreateDomainIds(Path processedDataPathBase, int untilBatch)
            throws IOException, SQLException
    {
        Collection<String> domainNamesAll = readDomainNames(processedDataPathBase, untilBatch);
        return getDatabaseIds(domainNamesAll);
    }

    Collection<String> readDomainNames(Path processedDataPathBase, int untilBatch) throws IOException {
        final Set<String> domainNamesAll = new HashSet<>(100_000);

        var domainFiles = ProcessedDataFileNames.listDomainFiles(processedDataPathBase, untilBatch);
        for (var file : domainFiles) {
            domainNamesAll.addAll(DomainRecordParquetFileReader.getDomainNames(file));
        }

        var linkFiles = ProcessedDataFileNames.listDomainLinkFiles(processedDataPathBase, untilBatch);
        for (var file : linkFiles) {
            domainNamesAll.addAll(DomainLinkRecordParquetFileReader.getDestDomainNames(file));
        }

        return domainNamesAll;
    }

    DomainIdRegistry getDatabaseIds(Collection<String> domainNamesAll) throws SQLException {
        DomainIdRegistry ret = new DomainIdRegistry();

        try (var conn = dataSource.getConnection();
             var insertStmt = conn.prepareStatement("""
                     INSERT IGNORE INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP) VALUES (?, ?)
                     """);
             var selectStmt = conn.prepareStatement("""
                     SELECT ID, DOMAIN_NAME FROM EC_DOMAIN WHERE DOMAIN_NAME=?
                     """)
        ) {

            int i = 0;
            for (var domain : domainNamesAll) {
                var parsed = new EdgeDomain(domain);
                insertStmt.setString(1, domain);
                insertStmt.setString(2, parsed.domain);
                insertStmt.addBatch();
                if (++i > 1000) {
                    i = 0;
                    insertStmt.executeBatch();
                }
            }
            if (i > 0) {
                insertStmt.executeBatch();
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
}
