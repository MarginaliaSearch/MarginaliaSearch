package nu.marginalia.wmsa.edge.assistant.screenshot;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.sql.SQLException;

import static java.lang.Integer.parseInt;

public class ScreenshotService {

    private final EdgeDataStoreDao edgeDataStoreDao;
    private final HikariDataSource dataSource;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ScreenshotService(EdgeDataStoreDao edgeDataStoreDao, HikariDataSource dataSource) {
        this.edgeDataStoreDao = edgeDataStoreDao;
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

        var domainName = edgeDataStoreDao.getDomain(new EdgeId<>(id)).map(Object::toString);
        if (domainName.isEmpty()) {
            Spark.halt(404);
        }

        response.type("image/svg+xml");
        return String.format("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<svg\n" +
                "   xmlns=\"http://www.w3.org/2000/svg\"\n" +
                "   width=\"640px\"\n" +
                "   height=\"480px\"\n" +
                "   viewBox=\"0 0 640 480\"\n" +
                "   version=\"1.1\">\n" +
                "  <g>\n" +
                "    <rect\n" +
                "       style=\"fill:#808080\"\n" +
                "       id=\"rect288\"\n" +
                "       width=\"595.41992\"\n" +
                "       height=\"430.01825\"\n" +
                "       x=\"23.034981\"\n" +
                "       y=\"27.850344\" />\n" +
                "    <text\n" +
                "       xml:space=\"preserve\"\n" +
                "       style=\"font-size:100px;fill:#909090;font-family:sans-serif;\"\n" +
                "       x=\"20\"\n" +
                "       y=\"120\">Placeholder</text>\n" +
                "    <text\n" +
                "       xml:space=\"preserve\"\n" +
                "       style=\"font-size:32px;fill:#000000;font-family:monospace;\"\n" +
                "       x=\"320\" y=\"240\" dominant-baseline=\"middle\" text-anchor=\"middle\">%s</text>\n" +
                "  </g>\n" +
                "</svg>\n", domainName.get());
    }
}
