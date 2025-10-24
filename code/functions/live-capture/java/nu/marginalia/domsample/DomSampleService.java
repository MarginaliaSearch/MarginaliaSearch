package nu.marginalia.domsample;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Named;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.domsample.db.DomSampleDb;
import nu.marginalia.livecapture.BrowserlessClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.service.module.ServiceConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DomSampleService {
    private final DomSampleDb db;
    private final HikariDataSource mariadbDataSource;
    private final int sampleThreads;
    private final DomainCoordinator domainCoordinator;
    private final URI browserlessURI;

    private static final Logger logger = LoggerFactory.getLogger(DomSampleService.class);
    private final ArrayBlockingQueue<EdgeDomain> samplingQueue = new ArrayBlockingQueue<>(4);
    private final Set<String> httpOnlyDomains = new HashSet<>(10_000);

    @Inject
    public DomSampleService(DomSampleDb db,
                            HikariDataSource mariadbDataSource,
                            @Named("browserless-uri") String browserlessAddress,
                            @Named("browserless-sample-threads") int sampleThreads,
                            DomainCoordinator domainCoordinator,
                            ServiceConfiguration serviceConfiguration)
            throws URISyntaxException
    {
        this.db = db;
        this.mariadbDataSource = mariadbDataSource;
        this.sampleThreads = sampleThreads;
        this.domainCoordinator = domainCoordinator;

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

        Thread.ofPlatform().daemon().start(this::mainThread);
        for (int i = 0; i < sampleThreads; i++) {
            Thread.ofPlatform().daemon().start(this::samplingThread);
        }
    }

    public void syncDomains() {
        Set<String> dbDomains = new HashSet<>();

        logger.info("Fetching domains from database...");

        try (var conn = mariadbDataSource.getConnection();
            var stmt = conn.prepareStatement("""
                SELECT DOMAIN_NAME, HTTP_SCHEMA
                FROM EC_DOMAIN 
                INNER JOIN DOMAIN_AVAILABILITY_INFORMATION
                ON EC_DOMAIN.ID=DOMAIN_ID
                WHERE NODE_AFFINITY>0
                AND BACKOFF_CONSECUTIVE_FAILURES<15
                """)
        ) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                String domainName = rs.getString("DOMAIN_NAME").toLowerCase();
                dbDomains.add(domainName);

                if ("HTTP".equalsIgnoreCase(rs.getString("HTTP_SCHEMA"))) {
                    httpOnlyDomains.add(domainName);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to sync domains", e);
        }

        logger.info("Found {} domains in database", dbDomains.size());

        db.syncDomains(dbDomains);

        logger.info("Synced domains to sqlite");
    }

    public void mainThread() {

        try (var client = new BrowserlessClient(browserlessURI)) {

            while (!Thread.currentThread().isInterrupted()) {

                try {
                    // Grace sleep in case we're operating on an empty domain list
                    TimeUnit.SECONDS.sleep(15);

                    syncDomains();
                    var domains = db.getScheduledDomains();

                    for (String domain : domains) {
                        samplingQueue.put(new EdgeDomain(domain));
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

    void samplingThread() {
        try (var client = new BrowserlessClient(browserlessURI)) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    EdgeDomain domain = samplingQueue.take();
                    try (var lock = domainCoordinator.lockDomain(domain)) {
                        updateDomain(client, domain.toString());
                    } catch (Exception e) {
                        logger.error("Error in DomSampleService run loop", e);
                    }
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    logger.info("DomSampleService interrupted, stopping...");
                    return;
                }
            }
        }
    }

    private void updateDomain(BrowserlessClient client, String domain) {

        String rootUrl;

        if (httpOnlyDomains.contains(domain.toLowerCase())) {
            rootUrl = "http://" + domain + "/";
        }
        else {
            rootUrl = "https://" + domain + "/";
        }

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
