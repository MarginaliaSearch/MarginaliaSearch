package nu.marginalia.wmsa.edge.search.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.wmsa.configuration.WebsiteUrl;
import nu.marginalia.wmsa.edge.dbcommon.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.sql.SQLException;

public class EdgeSearchAddToCrawlQueueService {

    private EdgeDataStoreDao edgeDataStoreDao;
    private WebsiteUrl websiteUrl;
    private HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(EdgeSearchAddToCrawlQueueService.class);

    @Inject
    public EdgeSearchAddToCrawlQueueService(EdgeDataStoreDao edgeDataStoreDao,
                                            WebsiteUrl websiteUrl,
                                            HikariDataSource dataSource) {
        this.edgeDataStoreDao = edgeDataStoreDao;
        this.websiteUrl = websiteUrl;
        this.dataSource = dataSource;
    }

    public Object suggestCrawling(Request request, Response response) throws SQLException {
        logger.info("{}", request.queryParams());
        int id = Integer.parseInt(request.queryParams("id"));
        boolean nomisclick = "on".equals(request.queryParams("nomisclick"));

        String domainName = getDomainName(id);

        if (nomisclick) {
            logger.info("Adding {} to crawl queue", domainName);
            addToCrawlQueue(id);
        }
        else {
            logger.info("Nomisclick not set, not adding {} to crawl queue", domainName);
        }

        response.redirect(websiteUrl.withPath("/site/" + domainName));

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

    private String getDomainName(int id) {
        var domain = edgeDataStoreDao.getDomain(new EdgeId<>(id));
        if (domain.isEmpty())
            Spark.halt(404);
        return domain.get().toString();
    }
}

