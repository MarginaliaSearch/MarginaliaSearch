package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.browse.RandomDomainSuggestionsDao;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.model.EdgeDomain;
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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

    public void register(Jooby jooby) throws IOException {
        var reviewRandomDomainsRenderer = rendererFactory.renderer("control/app/review-random-domains");
        var suggestionsRenderer = rendererFactory.renderer("control/app/random-domain-suggestions");

        jooby.get("/random-domains/review", ctx -> reviewRandomDomainsRenderer.render(reviewRandomDomainsModel(ctx)));
        jooby.post("/random-domains/review", this::reviewRandomDomainsAction);

        jooby.get("/random-domains/suggestions", ctx -> suggestionsRenderer.render(suggestionsModel(ctx)));
        jooby.post("/random-domains/suggestions/approve", this::approveSuggestionsAction);
        jooby.post("/random-domains/suggestions/reject", this::rejectSuggestionsAction);
    }

    private Object reviewRandomDomainsModel(Context ctx) throws SQLException {
        String afterVal = Objects.requireNonNullElse(ctx.lookup("after", QUERY, FORM).valueOrNull(), "0");
        int after = Integer.parseInt(afterVal);
        var domains = getDomains(after, 25);
        int nextAfter = domains.stream().mapToInt(RandomExplorationService.RandomDomainResult::id).max().orElse(Integer.MAX_VALUE);

        return Map.of("domains", domains,
                "after", nextAfter);

    }

    private Object reviewRandomDomainsAction(Context ctx) throws SQLException {
        removeRandomDomains(collectSelectedDomainIds(ctx));

        String after = ctx.lookup("after", QUERY, FORM).valueOrNull();

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

    private Object suggestionsModel(Context ctx) throws SQLException {
        String afterVal = Objects.requireNonNullElse(ctx.lookup("after", QUERY, FORM).valueOrNull(), "0");
        int after = Integer.parseInt(afterVal);
        var suggestions = suggestionsDao.listSuggestions(after, 25);
        int nextAfter = suggestions.stream()
                .mapToInt(RandomDomainSuggestionsDao.SuggestionRow::id)
                .max().orElse(Integer.MAX_VALUE);

        return Map.of("suggestions", suggestions,
                "after", nextAfter,
                "hasSuggestions", !suggestions.isEmpty());
    }

    private Object approveSuggestionsAction(Context ctx) throws SQLException {
        suggestionsDao.approveSuggestions(collectSelectedDomainIds(ctx));
        return suggestionsRedirect(ctx.lookup("after", QUERY, FORM).valueOrNull());
    }

    private Object rejectSuggestionsAction(Context ctx) throws SQLException {
        suggestionsDao.rejectSuggestions(collectSelectedDomainIds(ctx));
        return suggestionsRedirect(ctx.lookup("after", QUERY, FORM).valueOrNull());
    }

    private int[] collectSelectedDomainIds(Context ctx) {
        TIntArrayList idList = new TIntArrayList();
        Stream.concat(ctx.query().toMultimap().keySet().stream(), ctx.form().toMultimap().keySet().stream())
                .distinct().forEach(key -> {
            if (key.startsWith("domain-")) {
                String value = ctx.lookup(key, QUERY, FORM).valueOrNull();
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
