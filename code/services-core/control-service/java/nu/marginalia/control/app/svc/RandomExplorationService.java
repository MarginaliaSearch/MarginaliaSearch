package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.array.TIntArrayList;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.model.EdgeDomain;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RandomExplorationService {

    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;

    private ControlRendererFactory.Renderer reviewRandomDomainsRenderer;

    @Inject
    public RandomExplorationService(HikariDataSource dataSource,
                                    ControlRendererFactory rendererFactory
    ) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
    }

    public void register(Jooby jooby) throws IOException {
        reviewRandomDomainsRenderer = rendererFactory.renderer("control/app/review-random-domains");

        jooby.get("/review-random-domains", this::reviewRandomDomainsModel);
        jooby.post("/review-random-domains", this::reviewRandomDomainsAction);
    }

    private Object reviewRandomDomainsModel(Context ctx) throws SQLException {
        String afterVal = Objects.requireNonNullElse(ctx.query("after").valueOrNull(), "0");
        int after = Integer.parseInt(afterVal);
        var domains = getDomains(after, 25);
        int nextAfter = domains.stream().mapToInt(RandomExplorationService.RandomDomainResult::id).max().orElse(Integer.MAX_VALUE);

        ctx.setResponseType(MediaType.html);
        return reviewRandomDomainsRenderer.render(
                Map.of("domains", domains,
                       "after", nextAfter));
    }

    private Object reviewRandomDomainsAction(Context ctx) throws SQLException {
        TIntArrayList idList = new TIntArrayList();

        ctx.formMap().keySet().forEach(key -> {
            if (key.startsWith("domain-")) {
                String value = ctx.form(key).valueOrNull();
                if ("on".equalsIgnoreCase(value)) {
                    int id = Integer.parseInt(key.substring(7));
                    idList.add(id);
                }
            }
        });

        removeRandomDomains(idList.toArray());

        String after = ctx.form("after").valueOrNull();

        return """
                <?doctype html>
                <html><head><meta http-equiv="refresh" content="0;URL='/review-random-domains?after=%s'" /></head></html>
                """.formatted(after);
    }

    public void removeRandomDomains(int[] ids) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM EC_RANDOM_DOMAINS
                     WHERE DOMAIN_ID = ?
                     AND DOMAIN_SET = 0
                     """))
        {
            for (var id : ids) {
                stmt.setInt(1, id);
                stmt.addBatch();
            }
            stmt.executeBatch();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        }
    }

    public List<RandomDomainResult> getDomains(int afterId, int numResults) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT DOMAIN_ID, DOMAIN_NAME FROM EC_RANDOM_DOMAINS
                     INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID
                     WHERE DOMAIN_ID >= ?
                     LIMIT ?
                     """))
        {
            List<RandomDomainResult> ret = new ArrayList<>(numResults);
            stmt.setInt(1, afterId);
            stmt.setInt(2, numResults);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                ret.add(new RandomDomainResult(rs.getInt(1), rs.getString(2)));
            }
            return ret;
        }
    }

    public void removeDomain(EdgeDomain domain) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                DELETE EC_RANDOM_DOMAINS
                FROM EC_RANDOM_DOMAINS
                INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID = EC_RANDOM_DOMAINS.DOMAIN_ID
                WHERE EC_DOMAIN.DOMAIN_NAME = ?
             """))
        {
            stmt.setString(1, domain.toString());
            stmt.executeUpdate();
        }
    }


    public record RandomDomainResult(int id, String domainName) {}
}
