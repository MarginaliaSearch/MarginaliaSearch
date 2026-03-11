package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.ControlValidationError;
import nu.marginalia.control.Redirects;
import nu.marginalia.db.DomainRankingSetsService;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class ControlDomainRankingSetsService {
    private final ControlRendererFactory rendererFactory;
    private final DomainRankingSetsService domainRankingSetsService;

    private ControlRendererFactory.Renderer datasetsRenderer;
    private ControlRendererFactory.Renderer updateDatasetRenderer;
    private ControlRendererFactory.Renderer newDatasetRenderer;

    @Inject
    public ControlDomainRankingSetsService(ControlRendererFactory rendererFactory,
                                           DomainRankingSetsService domainRankingSetsService) {
        this.rendererFactory = rendererFactory;
        this.domainRankingSetsService = domainRankingSetsService;
    }

    public void register(Jooby jooby) throws IOException {
        datasetsRenderer = rendererFactory.renderer("control/sys/domain-ranking-sets");
        updateDatasetRenderer = rendererFactory.renderer("control/sys/update-domain-ranking-set");
        newDatasetRenderer = rendererFactory.renderer("control/sys/new-domain-ranking-set");

        jooby.get("/domain-ranking-sets", this::rankingSetsModel);
        jooby.get("/domain-ranking-sets/new", this::serveNewRankingSet);
        jooby.get("/domain-ranking-sets/{id}", this::rankingSetModel);
        jooby.post("/domain-ranking-sets/{id}", this::alterSetModel);
    }

    private Object rankingSetsModel(Context ctx) {
        ctx.setResponseType(MediaType.html);
        return datasetsRenderer.render(Map.of("rankingSets", domainRankingSetsService.getAll()));
    }

    private Object serveNewRankingSet(Context ctx) {
        ctx.setResponseType(MediaType.html);
        return newDatasetRenderer.render(new Object());
    }

    private Object rankingSetModel(Context ctx) throws SQLException {
        ctx.setResponseType(MediaType.html);
        DomainRankingSetsService.DomainRankingSet model = domainRankingSetsService.get(ctx.path("id").value()).orElseThrow();
        return updateDatasetRenderer.render(Map.of("rankingSet", model));
    }

    private Object alterSetModel(Context ctx) throws SQLException {
        final String act = ctx.query("act").valueOrNull();
        final String id = ctx.path("id").value();

        if ("update".equals(act)) {
            domainRankingSetsService.upsert(new DomainRankingSetsService.DomainRankingSet(
                    id,
                    ctx.form("description").valueOrNull(),
                    Integer.parseInt(ctx.form("depth").value()),
                    ctx.form("definition").valueOrNull()
            ));
        }
        else if ("delete".equals(act)) {
            DomainRankingSetsService.DomainRankingSet model = domainRankingSetsService.get(id).orElseThrow();
            if (model.isSpecial()) {
                throw new ControlValidationError("Cannot delete special ranking set",
                        """
                                SPECIAL data sets are reserved by the system and can not be deleted.
                                """,
                        "/domain-ranking-sets");
            }
            domainRankingSetsService.delete(model);
        }
        else if ("create".equals(act)) {
            if (domainRankingSetsService.get(ctx.form("name").valueOrNull()).isPresent()) {
                throw new ControlValidationError("Ranking set with that name already exists",
                        """
                                Ensure the new data set has a unique name and try again.
                                """,
                        "/domain-ranking-sets");
            }

            domainRankingSetsService.upsert(new DomainRankingSetsService.DomainRankingSet(
                    ctx.form("name").value("").toUpperCase(),
                    ctx.form("description").valueOrNull(),
                    Integer.parseInt(ctx.form("depth").value()),
                    ctx.form("definition").valueOrNull()
            ));
        }
        else {
            throw new ControlValidationError("Unknown action", """
                    An unknown action was requested and the system does not understand how to act on it.
                    """,
                "/domain-ranking-sets");
        }

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToRankingDataSets.render(null);
    }
}
