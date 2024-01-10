package nu.marginalia.linkdb.dlinks;

import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** DomainLinkDb that delegates to either a FileDomainLinkDb or a SqlDomainLinkDb,
 * depending on whether the file exists.  This is part of the migration path to
 * always using FileDomainLinkDb.
 */
public class SelectingDomainLinkDb implements DomainLinkDb {
    private final static Logger logger = LoggerFactory.getLogger(SelectingDomainLinkDb.class);

    private volatile DomainLinkDb currentDb;
    private final Path filename;
    public SelectingDomainLinkDb(@Named("domain-linkdb-file") Path filename,
                                 ServiceConfiguration serviceConfiguration,
                                 HikariDataSource dataSource) {
        this.filename = filename;

        // Load the database in a separate thread, so that the constructor can return
        // immediately.  This would otherwise add a lot of time to the startup of the
        // index service.

        Thread.ofPlatform().start(() -> {
            try {
                if (Files.exists(filename)) {
                    currentDb = new FileDomainLinkDb(filename);
                }
                else {
                    currentDb = new SqlDomainLinkDb(filename, dataSource, serviceConfiguration);
                }
                logger.info("Loaded linkdb");
            } catch (Exception e) {
                logger.error("Failed to load linkdb", e);
            }
        });
    }

    @Override
    public void switchInput(Path newFilename) throws Exception {
        Files.move(newFilename, filename, StandardCopyOption.REPLACE_EXISTING);

        Thread.ofPlatform().start(() -> {
            try {
                currentDb = new FileDomainLinkDb(filename);
            } catch (IOException e) {
                logger.error("Failed to load linkdb", e);
            }
        });

    }

    @Override
    public TIntArrayList findDestinations(int source) {
        // A race condition is not possible here, as the nullity of currentDb only changes from
        // null to non-null

        if (currentDb == null)
            return new TIntArrayList();

        return currentDb.findDestinations(source);
    }

    @Override
    public int countDestinations(int source) {
        if (currentDb == null)
            return 0;

        return currentDb.countDestinations(source);
    }

    @Override
    public TIntArrayList findSources(int dest) {
        if (currentDb == null)
            return new TIntArrayList();

        return currentDb.findSources(dest);
    }

    @Override
    public int countSources(int source) {
        if (currentDb == null)
            return 0;

        return currentDb.countSources(source);
    }

    @Override
    public void forEach(SourceDestConsumer consumer) {
        if (currentDb == null)
            throw new IllegalStateException("No linkdb loaded");

        currentDb.forEach(consumer);
    }
}
