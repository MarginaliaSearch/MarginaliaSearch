package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.app.model.BlacklistedDomainModel;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ControlBlacklistService {

    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ControlBlacklistService(HikariDataSource dataSource,
                                   ControlRendererFactory rendererFactory) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
    }


    public void register() throws IOException {
        var blacklistRenderer = rendererFactory.renderer("control/app/blacklist");

        Spark.get("/public/blacklist", this::blacklistModel, blacklistRenderer::render);
        Spark.post("/public/blacklist", this::updateBlacklist, Redirects.redirectToBlacklist);
    }

    private Object blacklistModel(Request request, Response response) {
        return Map.of("blacklist", lastNAdditions(100));
    }

    private Object updateBlacklist(Request request, Response response) {
        var domain = new EdgeDomain(request.queryParams("domain"));
        if ("add".equals(request.queryParams("act"))) {
            var comment = Objects.requireNonNullElse(request.queryParams("comment"), "");
            addToBlacklist(domain, comment);
        } else if ("del".equals(request.queryParams("act"))) {
            removeFromBlacklist(domain);
        }

        return "";
    }

    public void addToBlacklist(EdgeDomain domain, String comment) {
        logger.info("Blacklisting {} -- {}", domain, comment);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT IGNORE INTO EC_DOMAIN_BLACKLIST (URL_DOMAIN, COMMENT) VALUES (?, ?)
                     """)) {
            stmt.setString(1, domain.toString());
            stmt.setString(2, comment);
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void removeFromBlacklist(EdgeDomain domain) {
        logger.info("Un-blacklisting {}", domain);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM EC_DOMAIN_BLACKLIST WHERE URL_DOMAIN=?
                     """)) {
            stmt.setString(1, domain.toString());
            stmt.addBatch();
            stmt.setString(1, domain.topDomain);
            stmt.addBatch();
            stmt.executeBatch();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<BlacklistedDomainModel> lastNAdditions(int n) {
        final List<BlacklistedDomainModel> ret = new ArrayList<>(n);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT URL_DOMAIN, COMMENT
                     FROM EC_DOMAIN_BLACKLIST
                     ORDER BY ID DESC
                     LIMIT ?
                     """)) {
            stmt.setInt(1, n);

            var rs = stmt.executeQuery();
            while (rs.next()) {
                ret.add(new BlacklistedDomainModel(
                            new EdgeDomain(rs.getString("URL_DOMAIN")),
                            rs.getString("COMMENT")
                        )
                );
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return ret;

    }

}
