package nu.marginalia.domsample;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Named;
import nu.marginalia.domsample.db.DomSampleDb;
import nu.marginalia.livecapture.BrowserlessClient;
import nu.marginalia.service.module.ServiceConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DomSampleService {
    private final DomSampleDb db;
    private final HikariDataSource mariadbDataSource;
    private final URI browserlessURI;

    private static final Logger logger = LoggerFactory.getLogger(DomSampleService.class);

    @Inject
    public DomSampleService(DomSampleDb db,
                            HikariDataSource mariadbDataSource,
                            @Named("browserless-uri") String browserlessAddress,
                            ServiceConfiguration serviceConfiguration)
            throws URISyntaxException
    {
        this.db = db;
        this.mariadbDataSource = mariadbDataSource;

        if (StringUtils.isEmpty(browserlessAddress) || serviceConfiguration.node() > 1) {
            logger.warn("Live capture service will not run");
            browserlessURI = null;
        }
        else {
            browserlessURI = new URI(browserlessAddress);
        }
    }

    public void start() {
        if (browserlessURI == null) {
            logger.warn("DomSampleService is not enabled due to missing browserless URI or multi-node configuration");
            return;
        }

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

        try (var client = new BrowserlessClient(browserlessURI)) {

            while (!Thread.currentThread().isInterrupted()) {

                try {
                    // Grace sleep in case we're operating on an empty domain list
                    TimeUnit.SECONDS.sleep(15);

                    syncDomains();
                    var domains = db.getScheduledDomains();

                    for (var domain : domains) {
                        updateDomain(client, domain);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.info("DomSampleService interrupted, stopping...");
                    return;
                } catch (Exception e) {
                    logger.error("Error in DomSampleService run loop", e);
                }
            }

        }
    }

    private void updateDomain(BrowserlessClient client, String domain) {
        var rootUrl = "https://" + domain + "/";
        try {
            var content = client.annotatedContent(rootUrl, new BrowserlessClient.GotoOptions("load", Duration.ofSeconds(10).toMillis()));

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
