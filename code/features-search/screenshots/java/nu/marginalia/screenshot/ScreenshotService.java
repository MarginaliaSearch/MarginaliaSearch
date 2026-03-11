package nu.marginalia.screenshot;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Context;
import io.jooby.MediaType;
import nu.marginalia.db.DbDomainQueries;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    /** Jooby-compatible handler for screenshot requests */
    public Object serveScreenshotRequest(Context ctx) {
        String idStr = ctx.path("id").valueOrNull();
        if (Strings.isNullOrEmpty(idStr)) {
            ctx.sendRedirect("https://search.marginalia.nu/");
            return ctx;
        }

        int id = parseInt(idStr);

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
                ctx.setResponseType(MediaType.valueOf(rsp.getString(1)));
                ctx.setResponseCode(200);
                ctx.setResponseHeader("Cache-control", "public,max-age=3600");

                IOUtils.copy(rsp.getBlob(2).getBinaryStream(), ctx.responseStream());
                return ctx;
            }
        }
        catch (IOException ex) {
            logger.warn("IO error", ex);
        }
        catch (SQLException ex) {
            logger.warn("SQL error", ex);
        }

        return serveSvgPlaceholder(ctx, id);
    }

    private Object serveSvgPlaceholder(Context ctx, int id) {
        var name = domainQueries.getDomain(id).map(Object::toString)
                .orElse("[Screenshot Not Yet Captured]");

        ctx.setResponseType(MediaType.valueOf("image/svg+xml"));

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
