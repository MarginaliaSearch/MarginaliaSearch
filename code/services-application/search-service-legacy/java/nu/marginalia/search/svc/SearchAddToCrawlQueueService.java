package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Context;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Optional;

public class SearchAddToCrawlQueueService {

    private final DbDomainQueries domainQueries;
    private final WebsiteUrl websiteUrl;
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(SearchAddToCrawlQueueService.class);

    @Inject
    public SearchAddToCrawlQueueService(DbDomainQueries domainQueries,
                                        WebsiteUrl websiteUrl,
                                        HikariDataSource dataSource) {
        this.domainQueries = domainQueries;
        this.websiteUrl = websiteUrl;
        this.dataSource = dataSource;
    }

    public Object suggestCrawling(Context ctx) throws SQLException {
        int id = ctx.query("id").intValue();
        boolean nomisclick = "on".equals(ctx.query("nomisclick").value());

        var maybeDomainName = domainQueries.getDomain(id);
        if (maybeDomainName.isEmpty()) {
            ctx.setResponseCode(404);
            return "";
        }

        String domainName = maybeDomainName.map(EdgeDomain::toString).get();

        if (nomisclick) {
            logger.info("Adding {} to crawl queue", domainName);
            addToCrawlQueue(id);
        }
        else {
            logger.info("Nomisclick not set, not adding {} to crawl queue", domainName);
        }

        ctx.sendRedirect(websiteUrl.withPath("/site/" + domainName));

        return "";
    }

    private void addToCrawlQueue(int id) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT IGNORE INTO CRAWL_QUEUE(DOMAIN_NAME, SOURCE)
                     SELECT DOMAIN_NAME, "user" FROM EC_DOMAIN WHERE ID=?
                     """)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }


}

