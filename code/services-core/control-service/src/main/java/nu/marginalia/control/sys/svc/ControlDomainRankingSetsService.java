package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.db.DomainRankingSetsService;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class ControlDomainRankingSetsService {
    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;
    private final DomainRankingSetsService domainRankingSetsService;

    @Inject
    public ControlDomainRankingSetsService(HikariDataSource dataSource,
                                           ControlRendererFactory rendererFactory,
                                           DomainRankingSetsService domainRankingSetsService) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
        this.domainRankingSetsService = domainRankingSetsService;
    }

    public void register() throws IOException {
        var datasetsRenderer = rendererFactory.renderer("control/sys/domain-ranking-sets");
        var updateDatasetRenderer = rendererFactory.renderer("control/sys/update-domain-ranking-set");
        var newDatasetRenderer = rendererFactory.renderer("control/sys/new-domain-ranking-set");

        Spark.get("/public/domain-ranking-sets", this::rankingSetsModel, datasetsRenderer::render);
        Spark.get("/public/domain-ranking-sets/new", (rq,rs) -> new Object(), newDatasetRenderer::render);
        Spark.get("/public/domain-ranking-sets/:id", this::rankingSetModel, updateDatasetRenderer::render);
        Spark.post("/public/domain-ranking-sets/:id", this::alterSetModel, Redirects.redirectToRankingDataSets);
    }

    private Object alterSetModel(Request request, Response response) throws SQLException {
        final String act = request.queryParams("act");
        final String id = request.params("id");
        if ("update".equals(act)) {
            domainRankingSetsService.upsert(new DomainRankingSetsService.DomainRankingSet(
                    id,
                    request.queryParams("description"),
                    DomainRankingSetsService.DomainSetAlgorithm.valueOf(request.queryParams("algorithm")),
                    Integer.parseInt(request.queryParams("depth")),
                    request.queryParams("definition")
            ));
            return "";
        }
        else if ("delete".equals(act)) {
            var model = domainRankingSetsService.get(id).orElseThrow();
            if (model.isSpecial()) {
                throw new IllegalArgumentException("Cannot delete special ranking set");
            }
            domainRankingSetsService.delete(model);
            return "";
        }
        else if ("create".equals(act)) {
            if (domainRankingSetsService.get(request.queryParams("name")).isPresent()) {
                throw new IllegalArgumentException("Ranking set with that name already exists");
            }

            domainRankingSetsService.upsert(new DomainRankingSetsService.DomainRankingSet(
                    request.queryParams("name"),
                    request.queryParams("description"),
                    DomainRankingSetsService.DomainSetAlgorithm.valueOf(request.queryParams("algorithm")),
                    Integer.parseInt(request.queryParams("depth")),
                    request.queryParams("definition")
            ));
            return "";
        }

        throw new UnsupportedOperationException();
    }

    private Object rankingSetsModel(Request request, Response response) {
        return Map.of("rankingSets", domainRankingSetsService.getAll());
    }
    private Object rankingSetModel(Request request, Response response) throws SQLException {
        var model = domainRankingSetsService.get(request.params("id")).orElseThrow();
        return Map.of("rankingSet", model,
                "selectedAlgo", Map.of(
                        "special", model.algorithm() == DomainRankingSetsService.DomainSetAlgorithm.SPECIAL,
                        "adjacency_cheirank", model.algorithm() == DomainRankingSetsService.DomainSetAlgorithm.ADJACENCY_CHEIRANK,
                        "adjacency_pagerank", model.algorithm() == DomainRankingSetsService.DomainSetAlgorithm.ADJACENCY_PAGERANK,
                        "links_cheirank", model.algorithm() == DomainRankingSetsService.DomainSetAlgorithm.LINKS_CHEIRANK,
                        "links_pagerank", model.algorithm() == DomainRankingSetsService.DomainSetAlgorithm.LINKS_PAGERANK)
        );




    }
}
