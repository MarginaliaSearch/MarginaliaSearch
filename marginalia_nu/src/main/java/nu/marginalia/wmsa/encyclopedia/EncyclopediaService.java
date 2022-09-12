package nu.marginalia.wmsa.encyclopedia;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.util.Map;

public class EncyclopediaService extends Service {

    private static final Logger logger = LoggerFactory.getLogger(EncyclopediaService.class);
    private final MustacheRenderer<String> wikiErrorPageRenderer;
    private final MustacheRenderer<Object> wikiSearchResultRenderer;

    private final EncyclopediaDao encyclopediaDao;

    @Inject
    public EncyclopediaService(@Named("service-host") String ip,
                               @Named("service-port") Integer port,
                               EncyclopediaDao encyclopediaDao,
                               RendererFactory rendererFactory,
                               Initialization initialization,
                               MetricsServer metricsServer)
            throws IOException {

        super(ip, port, initialization, metricsServer);
        this.encyclopediaDao = encyclopediaDao;

        if (rendererFactory != null) {
            wikiErrorPageRenderer = rendererFactory.renderer("encyclopedia/wiki-error");
            wikiSearchResultRenderer = rendererFactory.renderer("encyclopedia/wiki-search");
        }
        else {
            wikiErrorPageRenderer = null;
            wikiSearchResultRenderer = null;
        }

        Gson gson = GsonFactory.get();

        Spark.get("/public/wiki/*", this::getWikiPage);
        Spark.get("/public/wiki-search", this::searchWikiPage);
        Spark.get("/encyclopedia/:term", (rq, rsp) -> encyclopediaDao.encyclopedia(rq.params("term")), gson::toJson);

        Spark.awaitInitialization();
    }

    @SneakyThrows
    private Object getWikiPage(Request req, Response rsp) {
        final String[] splats = req.splat();

        if (splats.length == 0)
            rsp.redirect("https://encyclopedia.marginalia.nu/wiki-start.html");

        final String name = splats[0];

        String pageName = encyclopediaDao.resolveEncylopediaRedirect(name).orElse(name);
        logger.info("Resolved {} -> {}", name, pageName);

        if (!encyclopediaDao.getWikiArticleData(name, rsp.raw().getOutputStream())) {
            return wikiErrorPageRenderer.render("https://en.wikipedia.org/wiki/" + name);
        }
        return "";
    }

    @SneakyThrows
    private Object searchWikiPage(Request req, Response rsp) {
        String term = req.queryParams("query");

        if (null == term) {
            rsp.redirect("https://encyclopedia.marginalia.nu/wiki-start.html");
            return "";
        }

        return wikiSearchResultRenderer.render(
                Map.of("query", term,
                        "results",
                        encyclopediaDao.findEncyclopediaPages(term))
        );
    }

}
