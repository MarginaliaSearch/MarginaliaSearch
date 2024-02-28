package nu.marginalia.crawl.spec;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class DbCrawlSpecProvider implements CrawlSpecProvider {
    private final HikariDataSource dataSource;
    private final ProcessConfiguration processConfiguration;
    private final DomainBlacklist blacklist;
    private List<CrawlSpecRecord> domains;

    private static final Logger logger = LoggerFactory.getLogger(DbCrawlSpecProvider.class);

    private static final double URL_GROWTH_FACTOR = Double.parseDouble(System.getProperty("crawler.crawlSetGrowthFactor", "1.25"));
    private static final int MIN_URLS_PER_DOMAIN = Integer.getInteger("crawler.minUrlsPerDomain", 100);
    private static final int MAX_URLS_PER_DOMAIN = Integer.getInteger("crawler.maxUrlsPerDomain", 10_000);

    @Inject
    public DbCrawlSpecProvider(HikariDataSource dataSource,
                               ProcessConfiguration processConfiguration,
                               DomainBlacklist blacklist
                               ) {
        this.dataSource = dataSource;
        this.processConfiguration = processConfiguration;
        this.blacklist = blacklist;
    }

    // Load the domains into memory to ensure the crawler is resilient to database blips
    private List<CrawlSpecRecord> loadData() throws Exception {
        var domains = new ArrayList<CrawlSpecRecord>();

        logger.info("Loading domains to be crawled");

        blacklist.waitUntilLoaded();

        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                     SELECT DOMAIN_NAME, COALESCE(KNOWN_URLS, 0), EC_DOMAIN.ID
                     FROM EC_DOMAIN
                     LEFT JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                     WHERE NODE_AFFINITY=?
                     """))
        {
            query.setInt(1, processConfiguration.node());
            query.setFetchSize(10_000);
            var rs = query.executeQuery();
            while (rs.next()) {
                // Skip blacklisted domains
                if (blacklist.isBlacklisted(rs.getInt(3)))
                    continue;

                var record = new CrawlSpecRecord(
                        rs.getString(1),
                        Math.clamp((int) (URL_GROWTH_FACTOR * rs.getInt(2)), MIN_URLS_PER_DOMAIN, MAX_URLS_PER_DOMAIN),
                        List.of()
                );

                domains.add(record);
            }
        }

        logger.info("Loaded {} domains", domains.size());

        // Shuffle the domains to ensure we get a good mix of domains in each crawl,
        // so that e.g. the big domains don't get all crawled at once, or we end up
        // crawling the same server in parallel from different subdomains...
        Collections.shuffle(domains);

        return domains;
    }

    /** Ensure that the domains in the parquet spec provider are loaded into
     *  the database. This mitigates the risk of certain footguns, such
     *  re-crawling before performing the 'Load' operation, which would
     *  otherwise result in the crawler not being able to find the domain
     *  in the database through the DbCrawlSpecProvider, and thus not
     *  being able to crawl it.
     * */
    public void ensureParquetDomainsLoaded(ParquetCrawlSpecProvider parquetCrawlSpecProvider) throws Exception {

        // This is a bit of an unhealthy mix of concerns, but it's for the Greater Good (TM)

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                INSERT IGNORE INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY)
                VALUES (?, ?, ?)
                """))
        {
            parquetCrawlSpecProvider.stream().forEach(record -> {
                try {
                    var domain = new EdgeDomain(record.getDomain());
                    stmt.setString(1, record.domain);
                    stmt.setString(2, domain.topDomain);
                    stmt.setInt(3, processConfiguration.node());
                    stmt.addBatch();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            stmt.executeBatch();
        }
    }

    @Override
    public int totalCount() throws Exception {
        if (domains == null) {
            domains = loadData();
        }
        return domains.size();
    }

    @SneakyThrows
    @Override
    public Stream<CrawlSpecRecord> stream() {
        if (domains == null) {
            domains = loadData();
        }

        return domains.stream();
    }


}
