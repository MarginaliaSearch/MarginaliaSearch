package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.app.model.BlacklistedDomainModel;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private ControlRendererFactory.Renderer blacklistRenderer;

    @Inject
    public ControlBlacklistService(HikariDataSource dataSource,
                                   ControlRendererFactory rendererFactory) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
    }


    public void register(Jooby jooby) throws IOException {
        blacklistRenderer = rendererFactory.renderer("control/app/blacklist");

        jooby.get("/blacklist", this::blacklistModel);
        jooby.post("/blacklist", this::updateBlacklist);
    }

    private Object blacklistModel(Context ctx) {
        ctx.setResponseType(MediaType.html);
        return blacklistRenderer.render(Map.of("blacklist", lastNAdditions(100)));
    }

    private Object updateBlacklist(Context ctx) {
        var domain = new EdgeDomain(ctx.form("domain").valueOrNull());
        if ("add".equals(ctx.query("act").valueOrNull())) {
            var comment = Objects.requireNonNullElse(ctx.form("comment").valueOrNull(), "");
            addToBlacklist(domain, comment);
        } else if ("del".equals(ctx.query("act").valueOrNull())) {
            removeFromBlacklist(domain);
        }

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToBlacklist.render(null);
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
