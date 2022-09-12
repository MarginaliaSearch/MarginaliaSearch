package nu.marginalia.wmsa.renderer;

import com.google.gson.Gson;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import nu.marginalia.wmsa.renderer.request.smhi.RenderSmhiIndexReq;
import nu.marginalia.wmsa.renderer.request.smhi.RenderSmhiPrognosReq;
import nu.marginalia.wmsa.resource_store.ResourceStoreClient;
import nu.marginalia.wmsa.resource_store.model.RenderedResource;
import nu.marginalia.wmsa.smhi.model.PrognosData;
import nu.marginalia.wmsa.smhi.model.index.IndexPlatser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class SmhiRendererService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final RendererFactory rendererFactory = new RendererFactory();

    private final MustacheRenderer<IndexPlatser> indexRenderer;
    private final MustacheRenderer<PrognosData> prognosRenderer;

    private final ResourceStoreClient resourceStoreClient;


    @Inject @SneakyThrows
    public SmhiRendererService(ResourceStoreClient resourceStoreClient) {
        this.resourceStoreClient = resourceStoreClient;
        indexRenderer = rendererFactory.renderer( "smhi/index");
        prognosRenderer = rendererFactory.renderer( "smhi/prognos");
    }

    public void start() {
        Spark.post("/render/smhi/index", this::renderSmhiIndex);
        Spark.post("/render/smhi/prognos", this::renderSmhiPrognos);
    }


    private Object renderSmhiIndex(Request request, Response response) {
        var requestText = request.body();
        var req = gson.fromJson(requestText, RenderSmhiIndexReq.class);

        logger.info("renderSmhiIndex()");
        var resource = new RenderedResource("index.html",
                LocalDateTime.MAX,
                indexRenderer.render(new IndexPlatser(req.platser)));

        resourceStoreClient.putResource(Context.fromRequest(request), "smhi", resource)
                .timeout(10, TimeUnit.SECONDS)
                .blockingSubscribe();

        return "";
    }

    private Object renderSmhiPrognos(Request request, Response response) {
        var requestText = request.body();
        var req = gson.fromJson(requestText, RenderSmhiPrognosReq.class);

        logger.info("renderSmhiPrognos({})", req.data.plats.namn);
        var resource = new RenderedResource(req.data.plats.getUrl(),
                LocalDateTime.now().plusHours(3),
                prognosRenderer.render(req.data));

        resourceStoreClient.putResource(Context.fromRequest(request), "smhi", resource)
                .timeout(10, TimeUnit.SECONDS)
                .blockingSubscribe();

        return "";
    }

}
