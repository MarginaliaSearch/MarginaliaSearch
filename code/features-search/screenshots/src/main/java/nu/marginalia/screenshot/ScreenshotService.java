package nu.marginalia.screenshot;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.dbcommon.DbDomainQueries;
import nu.marginalia.model.id.EdgeId;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.sql.SQLException;

import static java.lang.Integer.parseInt;

public class ScreenshotService {

    private final DbDomainQueries domainQueries;
    private final HikariDataSource dataSource;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ScreenshotService(DbDomainQueries dbDomainQueries, HikariDataSource dataSource) {
        this.domainQueries = dbDomainQueries;
        this.dataSource = dataSource;
    }

    public boolean hasScreenshot(EdgeId<EdgeDomain> domainId) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("""
                    SELECT TRUE
                        FROM DATA_DOMAIN_SCREENSHOT
                        INNER JOIN EC_DOMAIN ON EC_DOMAIN.DOMAIN_NAME=DATA_DOMAIN_SCREENSHOT.DOMAIN_NAME
                        WHERE EC_DOMAIN.ID=?
                    """)) {
            ps.setInt(1, domainId.id());
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBoolean(1);
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error", ex);
        }
        return false;
    }

    @SneakyThrows
    public Object serveScreenshotRequest(Request request, Response response) {
        if (Strings.isNullOrEmpty(request.params("id"))) {
            response.redirect("https://search.marginalia.nu/");
            return null;
        }

        int id = parseInt(request.params("id"));

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("""
                    SELECT CONTENT_TYPE, DATA
                        FROM DATA_DOMAIN_SCREENSHOT
                        INNER JOIN EC_DOMAIN ON EC_DOMAIN.DOMAIN_NAME=DATA_DOMAIN_SCREENSHOT.DOMAIN_NAME
                        WHERE EC_DOMAIN.ID=?
                    """)) {
            ps.setInt(1, id);
            var rsp = ps.executeQuery();
            if (rsp.next()) {
                response.type(rsp.getString(1));
                response.status(200);
                response.header("Cache-control", "public,max-age=3600");

                IOUtils.copy(rsp.getBlob(2).getBinaryStream(), response.raw().getOutputStream());
                return "";
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error", ex);
        }

        return serveSvgPlaceholder(response, id);
    }

    private Object serveSvgPlaceholder(Response response, int id) {

        var name = domainQueries.getDomain(new EdgeId<>(id)).map(Object::toString)
                .orElse("[Screenshot Not Yet Captured]");

        response.type("image/svg+xml");
        
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <svg
                   xmlns="http://www.w3.org/2000/svg"
                   width="640px"
                   height="480px"
                   viewBox="0 0 640 480"
                   version="1.1">
                  <g>
                    <rect
                       style="fill:#808080"
                       id="rect288"
                       width="595.41992"
                       height="430.01825"
                       x="23.034981"
                       y="27.850344" />
                    <text
                       xml:space="preserve"
                      style="font-size:100px;fill:#909090;font-family:sans-serif;"
                       x="20"
                       y="120">Placeholder</text>
                    <text
                       xml:space="preserve"
                       style="font-size:32px;fill:#000000;font-family:monospace;"
                       x="320" y="240" dominant-baseline="middle" text-anchor="middle">%s</text>
                  </g>
                </svg>\n
                """.formatted(name);
    }
}
