package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.app.model.DomainModel;
import nu.marginalia.control.app.model.DomainSearchResultModel;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.nodecfg.NodeConfigurationService;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

public class DomainsManagementService {

    private final HikariDataSource dataSource;
    private final NodeConfigurationService nodeConfigurationService;
    private final ControlRendererFactory rendererFactory;

    @Inject
    public DomainsManagementService(HikariDataSource dataSource,
                                    NodeConfigurationService nodeConfigurationService,
                                    ControlRendererFactory rendererFactory
                         ) {
        this.dataSource = dataSource;
        this.nodeConfigurationService = nodeConfigurationService;
        this.rendererFactory = rendererFactory;
    }

    public void register() throws IOException {

        var domainsViewRenderer = rendererFactory.renderer("control/app/domains");
        var addDomainsViewRenderer = rendererFactory.renderer("control/app/domains-new");
        var addDomainsAfterReportRenderer = rendererFactory.renderer("control/app/domains-new-report");

        Spark.get("/domain", this::getDomains, domainsViewRenderer::render);
        Spark.get("/domain/new", this::addDomains, addDomainsViewRenderer::render);
        Spark.post("/domain/new", this::addDomains, addDomainsAfterReportRenderer::render);
        Spark.post("/domain/:id/assign/:node", this::assignDomain, new Redirects.HtmlRedirect("/domain"));

    }

    private Object addDomains(Request request, Response response) throws SQLException {
        if ("GET".equals(request.requestMethod())) {
            return "";
        }
        else if ("POST".equals(request.requestMethod())) {
            String nodeStr = request.queryParams("node");
            String domainsStr = request.queryParams("domains");

            int node = Integer.parseInt(nodeStr);
            String[] domains = domainsStr.split("\n+");

            List<EdgeDomain> validDomains = new ArrayList<>();
            List<String> invalidDomains = new ArrayList<>();

            for (String domain : domains) {
                domain = domain.trim();
                if (domain.isBlank()) continue;
                if (domain.length() > 255) {
                    invalidDomains.add(domain);
                    continue;
                }
                if (domain.startsWith("#")) {
                    continue;
                }

                // Run through the URI parser to check for bad domains
                try {
                    if (domain.contains(":")) {
                        domain = new URI(domain ).toURL().getHost();
                    }
                    else {
                        domain = new URI("https://" + domain + "/").toURL().getHost();
                    }
                } catch (URISyntaxException | MalformedURLException e) {
                    invalidDomains.add(domain);
                    continue;
                }

                validDomains.add(new EdgeDomain(domain));
            }

            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement("INSERT IGNORE INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES (?, ?, ?)"))
            {
                for (var domain : validDomains) {
                    stmt.setString(1, domain.toString());
                    stmt.setString(2, domain.getTopDomain());
                    stmt.setInt(3, node);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            return Map.of("validDomains", validDomains,
                          "invalidDomains", invalidDomains);
        }
        return "";
    }

    private Object assignDomain(Request request, Response response) throws SQLException {

        String idStr = request.params(":id");
        String nodeStr = request.params(":node");

        int id = Integer.parseInt(idStr);
        int node = Integer.parseInt(nodeStr);

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("UPDATE EC_DOMAIN SET NODE_AFFINITY = ? WHERE ID = ?"))
        {
            stmt.setInt(1, node);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        }

        return "";
    }

    private DomainSearchResultModel getDomains(Request request, Response response) throws SQLException {
        List<DomainModel> ret = new ArrayList<>();

        String filterRaw = Objects.requireNonNullElse(request.queryParams("filter"), "*");

        String filter;
        if (filterRaw.isBlank()) filter = "%";
        else filter = filterRaw.replace('*', '%');

        int page = Integer.parseInt(Objects.requireNonNullElse(request.queryParams("page"), "0"));
        boolean hasMore = false;
        int count = 10;

        String field = Objects.requireNonNullElse(request.queryParams("field"), "domain");
        Map<String, Boolean> selectedField = Map.of(field, true);

        String affinity = Objects.requireNonNullElse(request.queryParams("affinity"), "all");
        Map<String, Boolean> selectedAffinity = Map.of(affinity, true);

        StringJoiner queryJoiner = new StringJoiner(" ");
        queryJoiner.add("""
                SELECT EC_DOMAIN.ID,
                       DOMAIN_NAME,
                       NODE_AFFINITY,
                       `RANK`,
                       IP,
                       EC_DOMAIN_BLACKLIST.URL_DOMAIN IS NOT NULL AS BLACKLISTED
                FROM WMSA_prod.EC_DOMAIN
                LEFT JOIN WMSA_prod.EC_DOMAIN_BLACKLIST ON DOMAIN_NAME = EC_DOMAIN_BLACKLIST.URL_DOMAIN
                """)
        .add((switch (field) {
            case "domain" -> "WHERE DOMAIN_NAME LIKE ?";
            case "ip" -> "WHERE IP LIKE ?";
            case "id" -> "WHERE EC_DOMAIN.ID = ?";
            default -> "WHERE DOMAIN_NAME LIKE ?";
        }))
        .add((switch (affinity) {
            case "assigned" -> "AND NODE_AFFINITY > 0";
            case "scheduled" -> "AND NODE_AFFINITY = 0";
            case "unassigned" -> "AND NODE_AFFINITY < 0";
            default -> "";
        }))
        .add("LIMIT ?")
        .add("OFFSET ?");


        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(queryJoiner.toString()))
        {
            stmt.setString(1, filter);
            stmt.setInt(2, count + 1);
            stmt.setInt(3, count * page);

            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (ret.size() == count) {
                        hasMore = true;
                        break;
                    }
                    ret.add(new DomainModel(
                            rs.getInt("ID"),
                            rs.getString("DOMAIN_NAME"),
                            rs.getString("IP"),
                            rs.getInt("NODE_AFFINITY"),
                            Math.round(100 * rs.getDouble("RANK"))/100.,
                            rs.getBoolean("BLACKLISTED")
                            ));
                }
            }
        }

        List<Integer> nodes = new ArrayList<>();

        for (var node : nodeConfigurationService.getAll()) {
            nodes.add(node.node());
        }

        return new DomainSearchResultModel(filterRaw,
                affinity,
                field,
                selectedAffinity,
                selectedField,
                page,
                hasMore,
                page > 0,
                nodes,
                ret);
    }

}
