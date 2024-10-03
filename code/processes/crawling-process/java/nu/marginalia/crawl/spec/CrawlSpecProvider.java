package nu.marginalia.crawl.spec;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Builder;
import lombok.SneakyThrows;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.model.EdgeDomain;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class CrawlSpecProvider {
    private final HikariDataSource dataSource;
    private final ProcessConfiguration processConfiguration;
    private final DomainBlacklist blacklist;

    private List<CrawlSpecRecord> domains;

    private static final Logger logger = LoggerFactory.getLogger(CrawlSpecProvider.class);

    private static final double URL_GROWTH_FACTOR = Double.parseDouble(System.getProperty("crawler.crawlSetGrowthFactor", "1.25"));
    private static final int MIN_URLS_PER_DOMAIN = Integer.getInteger("crawler.minUrlsPerDomain", 100);
    private static final int MID_URLS_PER_DOMAIN = Integer.getInteger("crawler.minUrlsPerDomain", 2_000);
    private static final int MAX_URLS_PER_DOMAIN = Integer.getInteger("crawler.maxUrlsPerDomain", 10_000);

    @Inject
    public CrawlSpecProvider(HikariDataSource dataSource,
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

        List<Integer> domainIds = new ArrayList<>(10_000);

        try (var conn = dataSource.getConnection();
             var assignFreeDomains = conn.prepareStatement("UPDATE EC_DOMAIN SET NODE_AFFINITY=? WHERE NODE_AFFINITY=0");
             var query = conn.prepareStatement("""
                     SELECT DOMAIN_NAME, COALESCE(KNOWN_URLS, 0), EC_DOMAIN.ID
                     FROM EC_DOMAIN
                     LEFT JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                     WHERE NODE_AFFINITY=? OR NODE_AFFINITY=0
                     """)
             )
        {

            // Assign any domains with node_affinity=0 to this node.  We must do this now, before we start crawling
            // to avoid race conditions with other crawl runs.  We don't want multiple crawlers to crawl the same domain.
            assignFreeDomains.setInt(1, processConfiguration.node());
            assignFreeDomains.executeUpdate();

            // Fetch the domains to be crawled
            query.setInt(1, processConfiguration.node());
            query.setFetchSize(10_000);
            var rs = query.executeQuery();

            while (rs.next()) {
                // Skip blacklisted domains
                int id = rs.getInt(3);
                if (blacklist.isBlacklisted(id))
                    continue;
                domainIds.add(id);

                int urls = rs.getInt(2);
                double growthFactor;

                if (urls < MID_URLS_PER_DOMAIN) {
                    growthFactor =  Math.max(2.5, URL_GROWTH_FACTOR);
                }
                else {
                    growthFactor = URL_GROWTH_FACTOR;
                }

                int urlsToFetch = Math.clamp((int) (growthFactor * rs.getInt(2)), MIN_URLS_PER_DOMAIN, MAX_URLS_PER_DOMAIN);

                var record = new CrawlSpecRecord(
                        rs.getString(1),
                        urlsToFetch,
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

    public List<EdgeDomain> getDomains() {
        return stream().map(CrawlSpecRecord::domain).map(EdgeDomain::new).toList();
    }

    public int totalCount() throws Exception {
        if (domains == null) {
            domains = loadData();
        }
        return domains.size();
    }

    @SneakyThrows
    public Stream<CrawlSpecRecord> stream() {
        if (domains == null) {
            domains = loadData();
        }

        return domains.stream();
    }


    @Builder
    public record CrawlSpecRecord(@NotNull String domain,
                                  int crawlDepth,
                                  @NotNull List<String> urls) {
        public CrawlSpecRecord(String domain, int crawlDepth) {
            this(domain, crawlDepth, List.of());
        }
    }
}
