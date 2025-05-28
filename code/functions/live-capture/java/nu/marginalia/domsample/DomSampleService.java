package nu.marginalia.domsample;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.domsample.db.DomSampleDb;
import nu.marginalia.livecapture.BrowserlessClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DomSampleService {
    private final DomSampleDb db;
    private final HikariDataSource mariadbDataSource;
    private final BrowserlessClient browserlessClient;

    private static final Logger logger = LoggerFactory.getLogger(DomSampleService.class);

    @Inject
    public DomSampleService(DomSampleDb db,
                            HikariDataSource mariadbDataSource,
                            BrowserlessClient browserlessClient) {
        this.db = db;
        this.mariadbDataSource = mariadbDataSource;
        this.browserlessClient = browserlessClient;

        Thread.ofPlatform().daemon().start(this::run);
    }

    public void syncDomains() {
        Set<String> dbDomains = new HashSet<>();

        logger.info("Fetching domains from database...");

        try (var conn = mariadbDataSource.getConnection();
            var stmt = conn.prepareStatement("""
                SELECT DOMAIN_NAME 
                FROM EC_DOMAIN 
                WHERE NODE_AFFINITY>0
                """)
        ) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                dbDomains.add(rs.getString("DOMAIN_NAME"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to sync domains", e);
        }

        logger.info("Found {} domains in database", dbDomains.size());

        db.syncDomains(dbDomains);

        logger.info("Synced domains to sqlite");
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Grace sleep in case we're operating on an empty domain list
                TimeUnit.SECONDS.sleep(15);

                syncDomains();
                var domains = db.getScheduledDomains();

                for (var domain : domains) {
                    updateDomain(domain);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("DomSampleService interrupted, stopping...");
                return;
            }
            catch (Exception e) {
                logger.error("Error in DomSampleService run loop", e);
            }
        }
    }

    private void updateDomain(String domain) {
        var rootUrl = "https://" + domain + "/";
        try {
            var content = browserlessClient.annotatedContent(rootUrl,
                    BrowserlessClient.GotoOptions.defaultValues());

            if (content.isPresent()) {
                db.saveSample(domain, rootUrl, content.get());
            }
        } catch (Exception e) {
            logger.error("Failed to process domain: " + domain, e);
        }
        finally {
            db.flagDomainAsFetched(domain);
        }
    }

}
