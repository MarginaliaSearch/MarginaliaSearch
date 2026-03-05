package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.db.DomainTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

@Singleton
public class DataSetsService {

    private static final Logger logger = LoggerFactory.getLogger(DataSetsService.class);

    private final ControlRendererFactory rendererFactory;
    private final DomainTypes domainTypes;

    private ControlRendererFactory.Renderer datasetsRenderer;

    @Inject
    public DataSetsService(ControlRendererFactory rendererFactory,
                           DomainTypes domainTypes) {
        this.rendererFactory = rendererFactory;
        this.domainTypes = domainTypes;
    }

    public void register(Jooby jooby) throws IOException {
        datasetsRenderer = rendererFactory.renderer("control/sys/data-sets");

        jooby.get("/datasets", this::serveDataSets);
        jooby.post("/datasets", this::updateDataSets);
    }

    private Object serveDataSets(Context ctx) {
        ctx.setResponseType(MediaType.html);
        return datasetsRenderer.render(dataSetsModel(ctx));
    }

    private Object updateDataSets(Context ctx) throws SQLException {
        updateUrl(DomainTypes.Type.BLOG, ctx.form("blogs").value(""));
        updateUrl(DomainTypes.Type.CRAWL, ctx.form("crawl").value(""));
        updateUrl(DomainTypes.Type.SMALL, ctx.form("smallweb").value(""));

        ctx.setResponseType(MediaType.html);
        return datasetsRenderer.render(dataSetsModel(ctx));
    }

    public Object dataSetsModel(Context ctx) {
        return Map.of(
                "blogs", domainTypes.getUrlForSelection(DomainTypes.Type.BLOG),
                "crawl", domainTypes.getUrlForSelection(DomainTypes.Type.CRAWL),
                "smallweb", domainTypes.getUrlForSelection(DomainTypes.Type.SMALL)
                );
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
