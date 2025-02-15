package nu.marginalia.assistant;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Context;
import nu.marginalia.db.DbDomainQueries;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

public class ScreenshotService {

    private final DbDomainQueries domainQueries;
    private final HikariDataSource dataSource;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ScreenshotService(DbDomainQueries dbDomainQueries, HikariDataSource dataSource) {
        this.domainQueries = dbDomainQueries;
        this.dataSource = dataSource;
    }

    public boolean hasScreenshot(int domainId) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("""
                    SELECT TRUE
                        FROM DATA_DOMAIN_SCREENSHOT
                        INNER JOIN EC_DOMAIN ON EC_DOMAIN.DOMAIN_NAME=DATA_DOMAIN_SCREENSHOT.DOMAIN_NAME
                        WHERE EC_DOMAIN.ID=?
                    """)) {
            ps.setInt(1, domainId);
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

    public Object serveScreenshotRequest(Context context) {
        if (Strings.isNullOrEmpty(context.path("id").value(""))) {
            context.setResponseCode(404);
            return "";
        }

        int id = context.path("id").intValue();

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
                context.setResponseType(rsp.getString(1));
                context.setResponseCode(200);
                context.setResponseHeader("Cache-control", "public,max-age=3600");

                IOUtils.copy(rsp.getBlob(2).getBinaryStream(), context.responseStream());
                return "";
            }
        }
        catch (IOException ex) {
            logger.warn("IO error", ex);
        }
        catch (SQLException ex) {
            logger.warn("SQL error", ex);
        }

        context.setResponseType("image/svg+xml");

        var name = domainQueries.getDomain(id).map(Object::toString)
                .orElse("[Screenshot Not Yet Captured]");

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
                </svg>
                """.formatted(name);
    }

}
