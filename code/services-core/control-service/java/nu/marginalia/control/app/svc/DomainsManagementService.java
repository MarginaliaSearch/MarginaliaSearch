package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.app.model.DomainModel;
import nu.marginalia.control.app.model.DomainSearchResultModel;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DomainsManagementService {

    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;

    @Inject
    public DomainsManagementService(HikariDataSource dataSource,
                                    ControlRendererFactory rendererFactory
                         ) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
    }

    public void register() throws IOException {

        var domainsViewRenderer = rendererFactory.renderer("control/app/domains");

        Spark.get("/domains", this::getDomains, domainsViewRenderer::render);

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


        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT EC_DOMAIN.ID,
                       DOMAIN_NAME,
                       NODE_AFFINITY,
                       `RANK`,
                       IP,
                       EC_DOMAIN_BLACKLIST.URL_DOMAIN IS NOT NULL AS BLACKLISTED
                FROM WMSA_prod.EC_DOMAIN
                LEFT JOIN WMSA_prod.EC_DOMAIN_BLACKLIST ON DOMAIN_NAME = EC_DOMAIN_BLACKLIST.URL_DOMAIN

                """
                     + // listen, I wouldn't worry about it
                     (switch (field) {
                         case "domain" -> "WHERE DOMAIN_NAME LIKE ?";
                         case "ip" -> "WHERE IP LIKE ?";
                         case "id" -> "WHERE EC_DOMAIN.ID = ?";
                         default -> "WHERE DOMAIN_NAME LIKE ?";
                     })
                    + " " +
                     (switch (affinity) {
                         case "assigned" -> "AND NODE_AFFINITY > 0";
                         case "scheduled" -> "AND NODE_AFFINITY = 0";
                         case "known" -> "AND NODE_AFFINITY < 0";
                         default -> "";
                     }) +

                """
                
                LIMIT ?
                OFFSET ?
                """
        ))
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

        return new DomainSearchResultModel(filterRaw,
                affinity,
                field,
                selectedAffinity,
                selectedField,
                page,
                hasMore,
                page > 0,
                ret);
    }

}
