package nu.marginalia.wmsa.edge.assistant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.reactivex.rxjava3.core.Observable;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.client.HttpStatusCode;
import nu.marginalia.wmsa.configuration.server.*;
import nu.marginalia.wmsa.edge.archive.client.ArchiveClient;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryService;
import nu.marginalia.wmsa.edge.assistant.eval.MathParser;
import nu.marginalia.wmsa.edge.assistant.eval.Units;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import nu.marginalia.wmsa.edge.assistant.suggest.Suggestions;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.Map;

public class EdgeAssistantService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = new GsonBuilder().create();
    private final Units units;
    private final DictionaryService dictionaryService;
    private final MathParser mathParser;
    private final ArchiveClient archiveClient;
    private final ScreenshotService screenshotService;
    private final MustacheRenderer<String> wikiErrorPageRenderer;
    private final MustacheRenderer<Object> wikiSearchResultRenderer;
    private final Suggestions suggestions;

    @SneakyThrows
    @Inject
    public EdgeAssistantService(@Named("service-host") String ip,
                                @Named("service-port") Integer port,
                                Initialization initialization,
                                MetricsServer metricsServer,
                                DictionaryService dictionaryService,
                                MathParser mathParser,
                                Units units,
                                ArchiveClient archiveClient,
                                RendererFactory rendererFactory,
                                ScreenshotService screenshotService,
                                Suggestions suggestions
                                )
    {
        super(ip, port, initialization, metricsServer);
        this.dictionaryService = dictionaryService;
        this.mathParser = mathParser;
        this.units = units;
        this.archiveClient = archiveClient;
        this.screenshotService = screenshotService;
        this.suggestions = suggestions;

        Spark.staticFiles.expireTime(600);

        if (rendererFactory != null) {
            wikiErrorPageRenderer = rendererFactory.renderer("encyclopedia/wiki-error");
            wikiSearchResultRenderer = rendererFactory.renderer("encyclopedia/wiki-search");
        }
        else {
            wikiErrorPageRenderer = null;
            wikiSearchResultRenderer = null;
        }

        Spark.get("/public/wiki/*", this::getWikiPage);
        Spark.get("/public/wiki-search", this::searchWikiPage);

        Spark.get("/public/screenshot/:id", screenshotService::serveScreenshotRequest);
        Spark.get("/screenshot/:id", screenshotService::serveScreenshotRequest);

        Spark.get("/dictionary/:word", (req, rsp) -> dictionaryService.define(req.params("word")), this::convertToJson);
        Spark.get("/spell-check/:term", (req, rsp) -> dictionaryService.spellCheck(req.params("term").toLowerCase()), this::convertToJson);
        Spark.get("/encyclopedia/:term", (req, rsp) -> dictionaryService.encyclopedia(req.params("term")), this::convertToJson);
        Spark.get("/unit-conversion", (req, rsp) -> unitConversion(
                rsp,
                req.queryParams("value"),
                req.queryParams("from"),
                req.queryParams("to")

        ));
        Spark.get("/eval-expression", (req, rsp) -> evalExpression(
                rsp,
                req.queryParams("value")
        ));

        Spark.get("/public/suggest/", this::getSuggestions, this::convertToJson);

        Spark.awaitInitialization();
    }

    private Object getSuggestions(Request request, Response response) {
        response.type("application/json");
        var param = request.queryParams("partial");
        if (param == null) {
            logger.warn("Bad parameter, partial is null");
            Spark.halt(500);
        }
        return suggestions.getSuggestions(10, param);
    }

    @SneakyThrows
    private Object getWikiPage(Request req, Response rsp) {
        final var ctx = Context.fromRequest(req);

        final String[] splats = req.splat();
        if (splats.length == 0)
            rsp.redirect("https://encyclopedia.marginalia.nu/wiki-start.html");


        final String s = splats[0];

        String pageName = dictionaryService.resolveEncylopediaRedirect(s).orElse(s);
        logger.info("Resolved {} -> {}", s, pageName);
        return archiveClient.getWiki(ctx, pageName)
                .onErrorResumeWith(resolveWikiPageNameWrongCase(ctx, s))
                .blockingFirst();
    }

    private Observable<String> resolveWikiPageNameWrongCase(Context ctx, String s) {
        var rsp = dictionaryService.findEncyclopediaPageDirect(s);
        if (rsp.isEmpty()) {
            return renderSearchPage(s);
        }
        return archiveClient.getWiki(ctx, rsp.get().getInternalName())
                            .onErrorResumeWith(renderSearchPage(s));
    }

    private Observable<String> renderSearchPage(String s) {
        return Observable.fromCallable(() -> wikiSearchResultRenderer.render(
                Map.of("query", s,
                        "error", "true",
                        "results", dictionaryService.findEncyclopediaPages(s))));
    }

    @SneakyThrows
    private Object searchWikiPage(Request req, Response rsp) {
        final var ctx = Context.fromRequest(req);

        String term = req.queryParams("query");
        if (null == term) {
            rsp.redirect("https://encyclopedia.marginalia.nu/wiki-start.html");
            return "";
        }

        return wikiSearchResultRenderer.render(
                Map.of("query", term,
                        "results",
                dictionaryService.findEncyclopediaPages(term))
        );
    }

    private Object evalExpression(Response rsp, String value) {
        try {
            var val = mathParser.evalFormatted(value);
            if (val.isBlank()) {
                Spark.halt(400);
                return null;
            }
            return val;
        }
        catch (Exception ex) {
            Spark.halt(400);
            return null;
        }
    }

    private Object unitConversion(Response rsp, String value, String fromUnit, String toUnit) {
        var result = units.convert(value, fromUnit, toUnit);
        if (result.isPresent()) {
            return result.get();
        }
        {
            Spark.halt(400);
            return null;
        }
    }

    private String convertToJson(Object o) {
        return gson.toJson(o);
    }

}
