package nu.marginalia.assistant;

import com.google.gson.Gson;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.assistant.suggest.Suggestions;
import nu.marginalia.functions.domains.DomainInfoGrpcService;
import nu.marginalia.functions.math.MathGrpcService;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.SparkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.List;

public class AssistantService extends SparkService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();
    private final Suggestions suggestions;

    @SneakyThrows
    @Inject
    public AssistantService(BaseServiceParams params,
                            ScreenshotService screenshotService,
                            DomainInfoGrpcService domainInfoGrpcService,
                            MathGrpcService mathGrpcService,
                            Suggestions suggestions)
    {
        super(params,
                ServicePartition.any(),
                List.of(domainInfoGrpcService, mathGrpcService));

        this.suggestions = suggestions;

        Spark.staticFiles.expireTime(600);

        Spark.get("/screenshot/:id", screenshotService::serveScreenshotRequest);
        Spark.get("/suggest/", this::getSuggestions, this::convertToJson);

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

    private String convertToJson(Object o) {
        return gson.toJson(o);
    }

}
