package nu.marginalia.db;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.set.hash.TIntHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@Singleton
public class DomainBlacklistImpl implements DomainBlacklist {
    private volatile TIntHashSet spamDomainSet = new TIntHashSet();
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final boolean blacklistDisabled = Boolean.getBoolean("blacklist.disable");

    private volatile boolean isLoaded = false;

    @Inject
    public DomainBlacklistImpl(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        Thread.ofPlatform().daemon().name("BlacklistUpdater").start(this::updateSpamList);
    }

    private void updateSpamList() {
        // If the blacklist is disabled, we don't need to do anything
        if (blacklistDisabled) {
            isLoaded = true;

            flagLoaded();

            return;
        }

        for (;;) {
            spamDomainSet = getSpamDomains();

            // Set the flag to true after the first loading attempt, regardless of success,
            // to avoid deadlocking threads that are waiting for this condition
            flagLoaded();

            // Sleep for 10 minutes before trying again
            try {
                TimeUnit.MINUTES.sleep(10);
            }
            catch (InterruptedException ex) {
                break;
            }
        }

    }

    private void flagLoaded() {
        if (!isLoaded) {
            synchronized (this) {
                isLoaded = true;
                notifyAll();
            }
        }
    }

    /** Block until the blacklist has been loaded */
    public boolean waitUntilLoaded() throws InterruptedException {
        if (!isLoaded) {
            synchronized (this) {
                while (!isLoaded) {
                    wait(5000);
                }
            }
        }

        return true;
    }


    public TIntHashSet getSpamDomains() {
        final TIntHashSet result = new TIntHashSet(1_000_000);

        if (blacklistDisabled) {
            return result;
        }

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("""
                    SELECT EC_DOMAIN.ID 
                    FROM EC_DOMAIN 
                    INNER JOIN EC_DOMAIN_BLACKLIST 
                    ON (EC_DOMAIN_BLACKLIST.URL_DOMAIN = EC_DOMAIN.DOMAIN_TOP 
                     OR EC_DOMAIN_BLACKLIST.URL_DOMAIN = EC_DOMAIN.DOMAIN_NAME)
                 """))
            {
                stmt.setFetchSize(1000);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    result.add(rsp.getInt(1));
                }
            }
        } catch (SQLException ex) {
            logger.error("Failed to load spam domain list", ex);
        }


        return result;
    }

    @Override
    public boolean isBlacklisted(int domainId) {

        if (spamDomainSet.contains(domainId)) {
            return true;
        }

        return false;
    }
}
