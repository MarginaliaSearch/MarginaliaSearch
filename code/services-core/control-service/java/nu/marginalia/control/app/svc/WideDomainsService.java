package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeProfile;
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
import java.util.Optional;

public class WideDomainsService {

    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;
    private final NodeConfigurationService nodeConfigurationService;
    private final ExecutorClient executorClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());

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

    public void register() throws IOException {
        var renderer = rendererFactory.renderer("control/app/wide-domains");

        Spark.get("/wide-domains", this::wideDomainsModel, renderer::render);
        Spark.post("/wide-domains", this::updateRoots, new Redirects.HtmlRedirect("/wide-domains"));
        Spark.post("/wide-domains/migrate", this::triggerMigration, new Redirects.HtmlRedirect("/wide-domains"));
    }

    private Object wideDomainsModel(Request request, Response response) {
        var wideNode = wideNodeId();
        return Map.of(
                "roots", listRoots(wideNode.orElse(-1)),
                "hasWideNode", wideNode.isPresent());
    }

    private Object updateRoots(Request request, Response response) {
        String topDomain = new EdgeDomain(request.queryParams("domain")).topDomain;

        if ("add".equals(request.queryParams("act"))) {
            addRoot(topDomain);
        } else if ("del".equals(request.queryParams("act"))) {
            removeRoot(topDomain);
        }

        return "";
    }

    private Object triggerMigration(Request request, Response response) {
        wideNodeId().ifPresent(nodeId -> executorClient.startFsm(nodeId, "MIGRATE_DOMAINS"));
        return "";
    }

    /** The id of the (single) node with the WIDE_DOMAINS profile, if one is configured. */
    private Optional<Integer> wideNodeId() {
        return nodeConfigurationService.getAll().stream()
                .filter(config -> !config.disabled())
                .filter(config -> config.profile() == NodeProfile.WIDE_DOMAINS)
                .map(config -> config.node())
                .findFirst();
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

    private List<WideDomainRoot> listRoots(int wideNodeId) {
        List<WideDomainRoot> roots = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT WIDE_DOMAIN_ROOTS.DOMAIN_TOP,
                           COUNT(EC_DOMAIN.ID) AS TOTAL,
                           COALESCE(SUM(EC_DOMAIN.NODE_AFFINITY = ?), 0) AS ON_WIDE
                    FROM WIDE_DOMAIN_ROOTS
                    LEFT JOIN EC_DOMAIN ON EC_DOMAIN.DOMAIN_TOP = WIDE_DOMAIN_ROOTS.DOMAIN_TOP
                    GROUP BY WIDE_DOMAIN_ROOTS.DOMAIN_TOP
                    ORDER BY WIDE_DOMAIN_ROOTS.DOMAIN_TOP
                    """)) {
            stmt.setInt(1, wideNodeId);

            var rs = stmt.executeQuery();
            while (rs.next()) {
                roots.add(new WideDomainRoot(
                        rs.getString("DOMAIN_TOP"),
                        rs.getInt("TOTAL"),
                        rs.getInt("ON_WIDE")));
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return roots;
    }

    public record WideDomainRoot(String domainTop, int totalDomains, int onWideNode) {}
}
