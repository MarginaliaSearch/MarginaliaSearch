package nu.marginalia.wmsa.edge.assistant;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.assistant.dict.DictionaryService;
import nu.marginalia.wmsa.edge.assistant.eval.MathParser;
import nu.marginalia.wmsa.edge.assistant.eval.Units;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import nu.marginalia.wmsa.edge.assistant.suggest.Suggestions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

public class EdgeAssistantService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();
    private final Units units;
    private final MathParser mathParser;
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
                                ScreenshotService screenshotService,
                                Suggestions suggestions
                                )
    {
        super(ip, port, initialization, metricsServer);
        this.mathParser = mathParser;
        this.units = units;
        this.suggestions = suggestions;

        Spark.staticFiles.expireTime(600);

        Spark.get("/public/screenshot/:id", screenshotService::serveScreenshotRequest);
        Spark.get("/screenshot/:id", screenshotService::serveScreenshotRequest);

        Spark.get("/dictionary/:word", (req, rsp) -> dictionaryService.define(req.params("word")), this::convertToJson);
        Spark.get("/spell-check/:term", (req, rsp) -> dictionaryService.spellCheck(req.params("term").toLowerCase()), this::convertToJson);
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
