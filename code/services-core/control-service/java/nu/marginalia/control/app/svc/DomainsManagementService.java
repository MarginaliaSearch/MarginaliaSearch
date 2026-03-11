package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.app.model.DomainModel;
import nu.marginalia.control.app.model.DomainSearchResultModel;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class DomainsManagementService {

    private final HikariDataSource dataSource;
    private final NodeConfigurationService nodeConfigurationService;
    private final ControlRendererFactory rendererFactory;

    private ControlRendererFactory.Renderer domainsViewRenderer;
    private ControlRendererFactory.Renderer addDomainsTxtViewRenderer;
    private ControlRendererFactory.Renderer addDomainsUrlViewRenderer;
    private ControlRendererFactory.Renderer addDomainsAfterReportRenderer;

    @Inject
    public DomainsManagementService(HikariDataSource dataSource,
                                    NodeConfigurationService nodeConfigurationService,
                                    ControlRendererFactory rendererFactory
                         ) {
        this.dataSource = dataSource;
        this.nodeConfigurationService = nodeConfigurationService;
        this.rendererFactory = rendererFactory;
    }

    public void register(Jooby jooby) throws IOException {

        domainsViewRenderer = rendererFactory.renderer("control/app/domains");
        addDomainsTxtViewRenderer = rendererFactory.renderer("control/app/domains-new");
        addDomainsUrlViewRenderer = rendererFactory.renderer("control/app/domains-new-url");
        addDomainsAfterReportRenderer = rendererFactory.renderer("control/app/domains-new-report");

        jooby.get("/domain", this::getDomains);
        jooby.get("/domain/new", this::addDomainsTextfield);
        jooby.post("/domain/new", this::addDomainsTextfieldPost);
        jooby.get("/domain/new-url", this::addDomainsFromDownload);
        jooby.post("/domain/new-url", this::addDomainsFromDownloadPost);
        jooby.post("/domain/{id}/assign/{node}", this::assignDomain);

    }

    private Object getDomains(Context ctx) throws SQLException {
        List<DomainModel> ret = new ArrayList<>();

        String filterRaw = Objects.requireNonNullElse(ctx.query("filter").valueOrNull(), "*");

        String filter;
        if (filterRaw.isBlank()) filter = "%";
        else filter = filterRaw.replace('*', '%');

        int page = Integer.parseInt(Objects.requireNonNullElse(ctx.query("page").valueOrNull(), "0"));
        boolean hasMore = false;
        int count = 10;

        String field = Objects.requireNonNullElse(ctx.query("field").valueOrNull(), "domain");
        Map<String, Boolean> selectedField = Map.of(field, true);

        String affinity = Objects.requireNonNullElse(ctx.query("affinity").valueOrNull(), "all");
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


        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryJoiner.toString()))
        {
            stmt.setString(1, filter);
            stmt.setInt(2, count + 1);
            stmt.setInt(3, count * page);

            try (ResultSet rs = stmt.executeQuery()) {
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

        for (NodeConfiguration node : nodeConfigurationService.getAll()) {
            nodes.add(node.node());
        }

        ctx.setResponseType(MediaType.html);
        return domainsViewRenderer.render(new DomainSearchResultModel(filterRaw,
                affinity,
                field,
                selectedAffinity,
                selectedField,
                page,
                hasMore,
                page > 0,
                nodes,
                ret));
    }

    private Object addDomainsTextfield(Context ctx) {
        ctx.setResponseType(MediaType.html);
        return addDomainsTxtViewRenderer.render("");
    }

    private Object addDomainsTextfieldPost(Context ctx) throws SQLException {
        String nodeStr = ctx.form("node").valueOrNull();
        String domainsStr = ctx.form("domains").valueOrNull();

        int node = Integer.parseInt(nodeStr);

        Map.Entry<List<EdgeDomain>, List<String>> domainsList = parseDomainsList(domainsStr);

        List<EdgeDomain> validDomains = domainsList.getKey();
        List<String> invalidDomains = domainsList.getValue();

        insertDomains(validDomains, node);

        ctx.setResponseType(MediaType.html);
        return addDomainsAfterReportRenderer.render(
                Map.of("validDomains", validDomains,
                       "invalidDomains", invalidDomains));
    }

    private Object addDomainsFromDownload(Context ctx) {
        ctx.setResponseType(MediaType.html);
        return addDomainsUrlViewRenderer.render("");
    }

    private Object addDomainsFromDownloadPost(Context ctx) throws Exception {
        String nodeStr = ctx.form("node").valueOrNull();
        URI domainsUrl = new URI(ctx.form("url").valueOrNull());

        int node = Integer.parseInt(nodeStr);

        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest httpReq = HttpRequest.newBuilder(domainsUrl).GET().build();

        HttpResponse<String> result = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (result.statusCode() != 200) {
            ctx.setResponseType(MediaType.html);
            return addDomainsAfterReportRenderer.render(Map.of("error", "Failed to download domains"));
        }
        Optional<String> ct = result.headers().firstValue("Content-Type");
        if (ct.isEmpty()) {
            ctx.setResponseType(MediaType.html);
            return addDomainsAfterReportRenderer.render(Map.of("error", "No content type"));
        }

        List<EdgeDomain> validDomains = new ArrayList<>();
        List<String> invalidDomains = new ArrayList<>();

        String contentType = ct.get().toLowerCase();

        if (contentType.startsWith("text/plain")) {
            Map.Entry<List<EdgeDomain>, List<String>> parsedDomains = parseDomainsList(result.body());
            validDomains = parsedDomains.getKey();
            invalidDomains = parsedDomains.getValue();
        }
        else {
            for (Element e : Jsoup.parse(result.body()).select("a")) {
                String s = e.attr("href");
                if (s.isBlank()) continue;
                if (!s.contains("://")) continue;

                URI uri = URI.create(s);
                String scheme = uri.getScheme();
                String host = uri.getHost();

                if (scheme == null || host == null)
                    continue;
                if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    continue;

                validDomains.add(new EdgeDomain(host));
            }
        }

        insertDomains(validDomains, node);

        ctx.setResponseType(MediaType.html);
        return addDomainsAfterReportRenderer.render(
                Map.of("validDomains", validDomains,
                       "invalidDomains", invalidDomains));
    }

    private Object assignDomain(Context ctx) throws SQLException {
        String idStr = ctx.path("id").value();
        String nodeStr = ctx.path("node").value();

        int id = Integer.parseInt(idStr);
        int node = Integer.parseInt(nodeStr);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE EC_DOMAIN SET NODE_AFFINITY = ? WHERE ID = ?"))
        {
            stmt.setInt(1, node);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        }

        ctx.setResponseType(MediaType.html);
        return new Redirects.HtmlRedirect("/domain").render(null);
    }

    private Map.Entry<List<EdgeDomain>, List<String>> parseDomainsList(String domainsStr) {
        List<EdgeDomain> validDomains = new ArrayList<>();
        List<String> invalidDomains = new ArrayList<>();

        for (String domain : domainsStr.split("\n+")) {
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

        return Map.entry(validDomains, invalidDomains);
    }

    private void insertDomains(List<EdgeDomain> domains, int node) throws SQLException {

        // Insert the domains into the database, updating the node affinity if the domain already exists and the affinity is not already set to a node
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                        INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE NODE_AFFINITY = IF(NODE_AFFINITY<=0, VALUES(NODE_AFFINITY), NODE_AFFINITY)
                        """))
        {
            for (EdgeDomain domain : domains) {
                stmt.setString(1, domain.toString());
                stmt.setString(2, domain.getTopDomain());
                stmt.setInt(3, node);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }


}
