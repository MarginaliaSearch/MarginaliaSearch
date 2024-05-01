package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.ControlValidationError;
import nu.marginalia.control.Redirects;
import nu.marginalia.db.DomainRankingSetsService;
import spark.Request;
import spark.Response;
import spark.Spark;

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

    public void register() throws IOException {
        var datasetsRenderer = rendererFactory.renderer("control/sys/domain-ranking-sets");
        var updateDatasetRenderer = rendererFactory.renderer("control/sys/update-domain-ranking-set");
        var newDatasetRenderer = rendererFactory.renderer("control/sys/new-domain-ranking-set");

        Spark.get("/domain-ranking-sets", this::rankingSetsModel, datasetsRenderer::render);
        Spark.get("/domain-ranking-sets/new", (rq,rs) -> new Object(), newDatasetRenderer::render);
        Spark.get("/domain-ranking-sets/:id", this::rankingSetModel, updateDatasetRenderer::render);
        Spark.post("/domain-ranking-sets/:id", this::alterSetModel, Redirects.redirectToRankingDataSets);
    }

    private Object alterSetModel(Request request, Response response) throws SQLException {
        final String act = request.queryParams("act");
        final String id = request.params("id");

        if ("update".equals(act)) {
            domainRankingSetsService.upsert(new DomainRankingSetsService.DomainRankingSet(
                    id,
                    request.queryParams("description"),
                    Integer.parseInt(request.queryParams("depth")),
                    request.queryParams("definition")
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
            if (domainRankingSetsService.get(request.queryParams("name")).isPresent()) {
                throw new ControlValidationError("Ranking set with that name already exists",
                        """
                                Ensure the new data set has a unique name and try again.
                                """,
                        "/domain-ranking-sets");
            }

            domainRankingSetsService.upsert(new DomainRankingSetsService.DomainRankingSet(
                    request.queryParams("name").toUpperCase(),
                    request.queryParams("description"),
                    Integer.parseInt(request.queryParams("depth")),
                    request.queryParams("definition")
            ));
            return "";
        }

        throw new ControlValidationError("Unknown action", """
                An unknown action was requested and the system does not understand how to act on it.
                """,
            "/domain-ranking-sets");
    }

    private Object rankingSetsModel(Request request, Response response) {
        return Map.of("rankingSets", domainRankingSetsService.getAll());
    }
    private Object rankingSetModel(Request request, Response response) throws SQLException {
        var model = domainRankingSetsService.get(request.params("id")).orElseThrow();
        return Map.of("rankingSet", model);
    }
}
