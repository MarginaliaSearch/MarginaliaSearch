package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeProfile;
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WideDomainsService {

    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;
    private final NodeConfigurationService nodeConfigurationService;
    private final ExecutorClient executorClient;

    @Inject
    public WideDomainsService(HikariDataSource dataSource,
                              ControlRendererFactory rendererFactory,
                              NodeConfigurationService nodeConfigurationService,
                              ExecutorClient executorClient) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
        this.nodeConfigurationService = nodeConfigurationService;
        this.executorClient = executorClient;
    }

    public void register(Jooby jooby) throws IOException {
        var renderer = rendererFactory.renderer("control/app/wide-domains");

        jooby.get("/wide-domains", ctx -> renderer.render(wideDomainsModel(ctx)));
        jooby.post("/wide-domains", ctx -> new Redirects.HtmlRedirect("/wide-domains").render(updateRoots(ctx)));
        jooby.post("/wide-domains/migrate", ctx -> new Redirects.HtmlRedirect("/wide-domains").render(triggerMigration(ctx)));
        jooby.post("/wide-domains/cleanup", ctx -> new Redirects.HtmlRedirect("/wide-domains").render(triggerCleanup(ctx)));
    }

    private Object wideDomainsModel(Context ctx) {
        return Map.of(
                "roots", listRoots(),
                "hasWideNode", wideNodeId().isPresent());
    }

    private Object updateRoots(Context ctx) {
        String domainParam = ctx.lookup("domain", QUERY, FORM).valueOrNull();
        if (domainParam == null || domainParam.isBlank()) {
            return "";
        }

        String topDomain = new EdgeDomain(domainParam).topDomain;

        if ("add".equals(ctx.lookup("act", QUERY, FORM).valueOrNull())) {
            addRoot(topDomain);
        } else if ("del".equals(ctx.lookup("act", QUERY, FORM).valueOrNull())) {
            removeRoot(topDomain);
        }

        return "";
    }

    private Object triggerMigration(Context ctx) {
        wideNodeId().ifPresent(nodeId -> executorClient.startFsm(nodeId, "MIGRATE_DOMAINS"));
        return "";
    }

    private Object triggerCleanup(Context ctx) {
        // Cleanup runs on the batch-capable nodes that domains may have been migrated away from.
        for (var config : nodeConfigurationService.getAll()) {
            if (config.disabled() || config.profile().isWideDomains() || !config.profile().permitBatchCrawl()) {
                continue;
            }
            executorClient.startFsm(config.node(), "CLEANUP_MIGRATED_DOMAINS");
        }
        return "";
    }

    /** The id of the node with the WIDE_DOMAINS profile, if one is configured. */
    private Optional<Integer> wideNodeId() {
        return nodeConfigurationService.getAll().stream()
                .filter(config -> !config.disabled())
                .filter(config -> config.profile() == NodeProfile.WIDE_DOMAINS)
                .map(config -> config.node())
                .min(Integer::compareTo);
    }

    private void addRoot(String topDomain) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("INSERT IGNORE INTO WIDE_DOMAIN_ROOTS (DOMAIN_TOP) VALUES (?)")) {
            stmt.setString(1, topDomain);
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void removeRoot(String topDomain) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("DELETE FROM WIDE_DOMAIN_ROOTS WHERE DOMAIN_TOP = ?")) {
            stmt.setString(1, topDomain);
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private List<String> listRoots() {
        List<String> roots = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT DOMAIN_TOP
                    FROM WIDE_DOMAIN_ROOTS
                    ORDER BY DOMAIN_TOP
                    """)) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                roots.add(rs.getString("DOMAIN_TOP"));
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return roots;
    }
}
