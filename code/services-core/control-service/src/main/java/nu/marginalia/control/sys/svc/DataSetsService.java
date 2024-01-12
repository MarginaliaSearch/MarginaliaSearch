package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.db.DomainTypes;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@Singleton
public class DataSetsService {

    private final HikariDataSource dataSource;
    private final ControlRendererFactory rendererFactory;
    private final DomainTypes domainTypes;

    @Inject
    public DataSetsService(HikariDataSource dataSource,
                           ControlRendererFactory rendererFactory,
                           DomainTypes domainTypes) {
        this.dataSource = dataSource;
        this.rendererFactory = rendererFactory;
        this.domainTypes = domainTypes;
    }

    public void register() throws IOException {
        var datasetsRenderer = rendererFactory.renderer("control/sys/data-sets");

        Spark.get("/public/datasets", this::dataSetsModel, datasetsRenderer::render);
        Spark.post("/public/datasets", this::updateDataSets, datasetsRenderer::render);
    }

    public Object dataSetsModel(Request request, Response response) {
        return Map.of(
                "blogs", domainTypes.getUrlForSelection(DomainTypes.Type.BLOG),
                "crawl", domainTypes.getUrlForSelection(DomainTypes.Type.CRAWL)
                );
    }

    public Object updateDataSets(Request request, Response response) throws SQLException {
        domainTypes.updateUrlForSelection(DomainTypes.Type.BLOG, request.queryParamOrDefault("blogs", ""));
        domainTypes.updateUrlForSelection(DomainTypes.Type.CRAWL, request.queryParamOrDefault("crawl", ""));

        return dataSetsModel(request, response);
    }


}
