package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.db.DomainTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

@Singleton
public class DataSetsService {

    private static final Logger logger = LoggerFactory.getLogger(DataSetsService.class);

    private final ControlRendererFactory rendererFactory;
    private final DomainTypes domainTypes;

    @Inject
    public DataSetsService(ControlRendererFactory rendererFactory,
                           DomainTypes domainTypes) {
        this.rendererFactory = rendererFactory;
        this.domainTypes = domainTypes;
    }

    public void register() throws IOException {
        var datasetsRenderer = rendererFactory.renderer("control/sys/data-sets");

        Spark.get("/datasets", this::dataSetsModel, datasetsRenderer::render);
        Spark.post("/datasets", this::updateDataSets, datasetsRenderer::render);
    }

    public Object dataSetsModel(Request request, Response response) {
        return Map.of(
                "blogs", domainTypes.getUrlForSelection(DomainTypes.Type.BLOG),
                "crawl", domainTypes.getUrlForSelection(DomainTypes.Type.CRAWL),
                "smallweb", domainTypes.getUrlForSelection(DomainTypes.Type.SMALLWEB)
                );
    }

    public Object updateDataSets(Request request, Response response) throws SQLException {

        updateUrl(DomainTypes.Type.BLOG, request.queryParamOrDefault("blogs", ""));
        updateUrl(DomainTypes.Type.CRAWL, request.queryParamOrDefault("crawl", ""));
        updateUrl(DomainTypes.Type.SMALLWEB, request.queryParamOrDefault("smallweb", ""));

        return dataSetsModel(request, response);
    }

    private void updateUrl(DomainTypes.Type type, String newValue) {
        var oldValue = domainTypes.getUrlForSelection(type);

        if (!Objects.equals(oldValue, newValue)) {
            try {
                logger.info("Updating data set url for {} to {}", type, newValue);
                domainTypes.updateUrlForSelection(type, newValue);
                if (!newValue.isBlank()) {
                    logger.info("Downloading url set");
                    domainTypes.reloadDomainsList(type);
                }
            } catch (Exception e) {
                logger.error("Failed to update URL", e);
            }
        }
    }


}
