package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.MapModelAndView;
import io.jooby.ModelAndView;
import io.jooby.annotation.FormParam;
import io.jooby.annotation.POST;
import io.jooby.annotation.Path;
import nu.marginalia.db.DbDomainQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;

public class SearchAddToCrawlQueueService {

    private final DbDomainQueries domainQueries;
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(SearchAddToCrawlQueueService.class);

    @Inject
    public SearchAddToCrawlQueueService(DbDomainQueries domainQueries,
                                        HikariDataSource dataSource) {
        this.domainQueries = domainQueries;
        this.dataSource = dataSource;
    }

    @POST
    @Path("/site/suggest/")
    public ModelAndView<?> suggestCrawling(
            @FormParam int id,
            @FormParam String nomisclick
    ) throws SQLException {

        String domainName = getDomainName(id);

        if ("on".equals(nomisclick)) {
            logger.info("Adding {} to crawl queue", domainName);
            addToCrawlQueue(id);
        }
        else {
            logger.info("Nomisclick not set, not adding {} to crawl queue", domainName);
        }

        return new MapModelAndView("redirect.jte", Map.of("url", "/site/"+domainName));
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

    private String getDomainName(int id) {
        var domain = domainQueries.getDomain(id);
        if (domain.isEmpty())
            throw new IllegalArgumentException();
        return domain.get().toString();
    }
}

