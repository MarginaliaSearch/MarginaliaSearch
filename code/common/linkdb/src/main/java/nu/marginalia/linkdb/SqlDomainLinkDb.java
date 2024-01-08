package nu.marginalia.linkdb;

import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** DomainLinkDb implementation that goes through the motions of
 * being a File-backed DomainLinkDb, but actually uses the legacy SQL database
 * for loading the data.
 * <p>
 * This is part of the migration path to using FileDomainLinkDb.
 */
public class SqlDomainLinkDb implements DomainLinkDb {
    private volatile long[] sourceToDest = new long[0];
    private volatile long[] destToSource = new long[0];
    private static final Logger logger = LoggerFactory.getLogger(SqlDomainLinkDb.class);
    
    private final Path filename;
    private final HikariDataSource dataSource;
    private final int node;

    public SqlDomainLinkDb(@Named("domain-linkdb-file") Path filename,
                           HikariDataSource dataSource,
                           ServiceConfiguration configuration)
    {
        this.filename = filename;
        this.dataSource = dataSource;

        node = configuration.node();

        Thread.ofPlatform().start(() -> {
            try {
                loadDb();
            } catch (Exception e) {
                logger.error("Failed to load linkdb", e);
            }
        });
    }

    @Override
    public void switchInput(Path newFilename) throws IOException {
        Files.move(newFilename, filename, StandardCopyOption.REPLACE_EXISTING);

        loadDb();
    }

    public void loadDb() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
            STR."""
                SELECT
                    SOURCE_DOMAIN_ID,
                    DEST_DOMAIN_ID
                FROM EC_DOMAIN_LINK
                INNER JOIN EC_DOMAIN
                    ON EC_DOMAIN.ID = EC_DOMAIN_LINK.SOURCE_DOMAIN_ID
                WHERE NODE_AFFINITY=\{node}
                """);
             var rs = stmt.executeQuery())
        {
            TLongArrayList sourceToDest = new TLongArrayList(10_000_000);
            TLongArrayList destToSource = new TLongArrayList(10_000_000);

            while (rs.next()) {
                long source = Integer.toUnsignedLong(rs.getInt(1));
                long dest = Integer.toUnsignedLong(rs.getInt(2));

                sourceToDest.add((source << 32) | dest);
                destToSource.add((dest << 32) | source);
            }

            sourceToDest.sort();
            destToSource.sort();

            this.sourceToDest = sourceToDest.toArray();
            this.destToSource = destToSource.toArray();
        }
        catch (Exception ex) {
            logger.error("Failed to load linkdb", ex);
        }

        logger.info("LinkDB loaded, size = {}", sourceToDest.length);
    }

    @Override
    public TIntArrayList findDestinations(int source) {
        return findRelated(sourceToDest, source);
    }

    @Override
    public TIntArrayList findSources(int dest) {
        return findRelated(destToSource, dest);
    }

    @Override
    public int countDestinations(int source) {
        return countRelated(sourceToDest, source);
    }

    @Override
    public int countSources(int dest) {
        return countRelated(destToSource, dest);
    }

    @Override
    public void forEach(SourceDestConsumer consumer) {
        for (long val : sourceToDest) {
            consumer.accept((int) (val >>> 32), (int) (val & 0xFFFF_FFFFL));
        }
    }

    private TIntArrayList findRelated(long[] range, int key) {
        long keyLong = Integer.toUnsignedLong(key) << 32;
        long nextKeyLong = Integer.toUnsignedLong(key + 1) << 32;

        int start = Arrays.binarySearch(range, keyLong);

        if (start < 0) {
            // Key is not found, get the insertion point
            start = -start - 1;
        }

        TIntArrayList result = new TIntArrayList();

        for (int i = start; i < range.length && range[i] < nextKeyLong; i++) {
            result.add((int) (range[i] & 0xFFFF_FFFFL));
        }

        return result;
    }

    private int countRelated(long[] range, int key) {
        long keyLong = Integer.toUnsignedLong(key) << 32;
        long nextKeyLong = Integer.toUnsignedLong(key + 1) << 32;

        int start = Arrays.binarySearch(range, keyLong);

        if (start < 0) {
            // Key is not found, get the insertion point
            start = -start - 1;
        }

        int num = 0;
        for (int i = start; i < range.length && range[i] < nextKeyLong; i++, num++);
        return num;
    }

}
