package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.browse.RandomDomainSuggestionsDao;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.model.EdgeDomain;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RandomExplorationService {

    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;
    private final RandomDomainSuggestionsDao suggestionsDao;

    @Inject
    public RandomExplorationService(HikariDataSource dataSource,
                                    ControlRendererFactory rendererFactory,
                                    RandomDomainSuggestionsDao suggestionsDao
    ) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
        this.suggestionsDao = suggestionsDao;
    }

    public void register() throws IOException {
        var reviewRandomDomainsRenderer = rendererFactory.renderer("control/app/review-random-domains");
        var suggestionsRenderer = rendererFactory.renderer("control/app/random-domain-suggestions");

        Spark.get("/random-domains/review", this::reviewRandomDomainsModel, reviewRandomDomainsRenderer::render);
        Spark.post("/random-domains/review", this::reviewRandomDomainsAction);

        Spark.get("/random-domains/suggestions", this::suggestionsModel, suggestionsRenderer::render);
        Spark.post("/random-domains/suggestions/approve", this::approveSuggestionsAction);
        Spark.post("/random-domains/suggestions/reject", this::rejectSuggestionsAction);
    }

    private Object reviewRandomDomainsModel(Request request, Response response) throws SQLException {
        String afterVal = Objects.requireNonNullElse(request.queryParams("after"), "0");
        int after = Integer.parseInt(afterVal);
        var domains = getDomains(after, 25);
        int nextAfter = domains.stream().mapToInt(RandomExplorationService.RandomDomainResult::id).max().orElse(Integer.MAX_VALUE);

        return Map.of("domains", domains,
                "after", nextAfter);

    }

    private Object reviewRandomDomainsAction(Request request, Response response) throws SQLException {
        removeRandomDomains(collectSelectedDomainIds(request));

        String after = request.queryParams("after");

        return """
                <?doctype html>
                <html><head><meta http-equiv="refresh" content="0;URL='/random-domains/review?after=%s'" /></head></html>
                """.formatted(after);
    }

    public void removeRandomDomains(int[] ids) throws SQLException {
        // /random-domains/review lists rows from every set; matching that, the
        // delete is unfiltered by set so operators can prune any visible row.
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     DELETE FROM EC_RANDOM_DOMAINS
                     WHERE DOMAIN_ID = ?
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

    private Object suggestionsModel(Request request, Response response) throws SQLException {
        String afterVal = Objects.requireNonNullElse(request.queryParams("after"), "0");
        int after = Integer.parseInt(afterVal);
        var suggestions = suggestionsDao.listSuggestions(after, 25);
        int nextAfter = suggestions.stream()
                .mapToInt(RandomDomainSuggestionsDao.SuggestionRow::id)
                .max().orElse(Integer.MAX_VALUE);

        return Map.of("suggestions", suggestions,
                "after", nextAfter,
                "hasSuggestions", !suggestions.isEmpty());
    }

    private Object approveSuggestionsAction(Request request, Response response) throws SQLException {
        suggestionsDao.approveSuggestions(collectSelectedDomainIds(request));
        return suggestionsRedirect(request.queryParams("after"));
    }

    private Object rejectSuggestionsAction(Request request, Response response) throws SQLException {
        suggestionsDao.rejectSuggestions(collectSelectedDomainIds(request));
        return suggestionsRedirect(request.queryParams("after"));
    }

    private int[] collectSelectedDomainIds(Request request) {
        TIntArrayList idList = new TIntArrayList();
        request.queryParams().forEach(key -> {
            if (key.startsWith("domain-")) {
                String value = request.queryParams(key);
                if ("on".equalsIgnoreCase(value)) {
                    int id = Integer.parseInt(key.substring(7));
                    idList.add(id);
                }
            }
        });
        return idList.toArray();
    }

    private String suggestionsRedirect(String after) {
        String afterParam = (after == null || after.isBlank()) ? "0" : after;
        return """
                <?doctype html>
                <html><head><meta http-equiv="refresh" content="0;URL='/random-domains/suggestions?after=%s'" /></head></html>
                """.formatted(afterParam);
    }
}
