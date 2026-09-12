package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.ControlValidationError;
import nu.marginalia.control.Redirects;
import nu.marginalia.db.DomainRankingSetsService;
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class ControlDomainRankingSetsService {
    private final ControlRendererFactory rendererFactory;
    private final DomainRankingSetsService domainRankingSetsService;

    @Inject
    public ControlDomainRankingSetsService(ControlRendererFactory rendererFactory,
                                           DomainRankingSetsService domainRankingSetsService) {
        this.rendererFactory = rendererFactory;
        this.domainRankingSetsService = domainRankingSetsService;
    }

    public void register(Jooby jooby) throws IOException {
        var datasetsRenderer = rendererFactory.renderer("control/sys/domain-ranking-sets");
        var updateDatasetRenderer = rendererFactory.renderer("control/sys/update-domain-ranking-set");
        var newDatasetRenderer = rendererFactory.renderer("control/sys/new-domain-ranking-set");

        jooby.get("/domain-ranking-sets", ctx -> datasetsRenderer.render(rankingSetsModel(ctx)));
        jooby.get("/domain-ranking-sets/new", ctx -> newDatasetRenderer.render(new Object()));
        jooby.get("/domain-ranking-sets/{id}", ctx -> updateDatasetRenderer.render(rankingSetModel(ctx)));
        jooby.post("/domain-ranking-sets/{id}", ctx -> Redirects.redirectToRankingDataSets.render(alterSetModel(ctx)));
    }

    private Object alterSetModel(Context ctx) throws SQLException {
        final String act = ctx.lookup("act", QUERY, FORM).valueOrNull();
        final String id = ctx.path("id").value();

        if ("update".equals(act)) {
            domainRankingSetsService.upsert(new DomainRankingSetsService.DomainRankingSet(
                    id,
                    ctx.lookup("description", QUERY, FORM).valueOrNull(),
                    Integer.parseInt(ctx.lookup("depth", QUERY, FORM).valueOrNull()),
                    ctx.lookup("definition", QUERY, FORM).valueOrNull()
            ));
            return "";
        }
        else if ("delete".equals(act)) {
            var model = domainRankingSetsService.get(id).orElseThrow();
            if (model.isSpecial()) {
                throw new ControlValidationError("Cannot delete special ranking set",
                        """
                                SPECIAL data sets are reserved by the system and can not be deleted.
                                """,
                        "/domain-ranking-sets");
            }
            domainRankingSetsService.delete(model);
            return "";
        }
        else if ("create".equals(act)) {
            if (domainRankingSetsService.get(ctx.lookup("name", QUERY, FORM).valueOrNull()).isPresent()) {
                throw new ControlValidationError("Ranking set with that name already exists",
                        """
                                Ensure the new data set has a unique name and try again.
                                """,
                        "/domain-ranking-sets");
            }

            domainRankingSetsService.upsert(new DomainRankingSetsService.DomainRankingSet(
                    ctx.lookup("name", QUERY, FORM).valueOrNull().toUpperCase(),
                    ctx.lookup("description", QUERY, FORM).valueOrNull(),
                    Integer.parseInt(ctx.lookup("depth", QUERY, FORM).valueOrNull()),
                    ctx.lookup("definition", QUERY, FORM).valueOrNull()
            ));
            return "";
        }

        throw new ControlValidationError("Unknown action", """
                An unknown action was requested and the system does not understand how to act on it.
                """,
            "/domain-ranking-sets");
    }

    private Object rankingSetsModel(Context ctx) {
        return Map.of("rankingSets", domainRankingSetsService.getAll());
    }
    private Object rankingSetModel(Context ctx) throws SQLException {
        var model = domainRankingSetsService.get(ctx.path("id").value()).orElseThrow();
        return Map.of("rankingSet", model);
    }
}
