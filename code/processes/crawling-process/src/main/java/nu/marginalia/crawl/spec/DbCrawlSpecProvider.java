package nu.marginalia.crawl.spec;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DbCrawlSpecProvider implements CrawlSpecProvider {
    private final HikariDataSource dataSource;
    private final ProcessConfiguration processConfiguration;
    private List<CrawlSpecRecord> domains;

    private static final Logger logger = LoggerFactory.getLogger(DbCrawlSpecProvider.class);

    @Inject
    public DbCrawlSpecProvider(HikariDataSource dataSource,
                               ProcessConfiguration processConfiguration
                               ) {
        this.dataSource = dataSource;
        this.processConfiguration = processConfiguration;
    }

    // Load the domains into memory to ensure the crawler is resilient to database blips
    private List<CrawlSpecRecord> loadData() throws SQLException {
        var domains = new ArrayList<CrawlSpecRecord>();

        logger.info("Loading domains to be crawled");

        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                     SELECT DOMAIN_NAME, COALESCE(KNOWN_URLS, 0)
                     FROM EC_DOMAIN
                     LEFT JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                     WHERE NODE_AFFINITY=?
                     """))
        {
            query.setInt(1, processConfiguration.node());
            query.setFetchSize(10_000);
            var rs = query.executeQuery();
            while (rs.next()) {
                domains.add(new CrawlSpecRecord(
                                rs.getString(1),
                                Math.clamp((int) (1.25 * rs.getInt(2)), 250, 10_000),
                                List.of()
                        ));
            }
        }

        logger.info("Loaded {} domains", domains.size());

        return domains;
    }


    @Override
    public int totalCount() throws SQLException {
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
