package nu.marginalia.wmsa.edge.search.svc;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.api.model.ApiSearchResult;
import nu.marginalia.wmsa.api.model.ApiSearchResults;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.search.EdgeSearchOperator;
import nu.marginalia.wmsa.edge.search.command.SearchJsParameter;
import nu.marginalia.wmsa.edge.search.model.EdgeSearchProfile;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import spark.Request;
import spark.Response;

import java.util.stream.Collectors;

public class EdgeSearchApiQueryService {
    private EdgeSearchOperator searchOperator;

    @Inject
    public EdgeSearchApiQueryService(EdgeSearchOperator searchOperator) {
        this.searchOperator = searchOperator;
    }

    @SneakyThrows
    public Object apiSearch(Request request, Response response) {

        final var ctx = Context.fromRequest(request);
        final String queryParam = request.queryParams("query");
        final int limit;
        EdgeSearchProfile profile = EdgeSearchProfile.YOLO;

        String count = request.queryParamOrDefault("count", "20");
        limit = Integer.parseInt(count);

        String index = request.queryParamOrDefault("index", "0");
        if (!Strings.isNullOrEmpty(index)) {
            profile = switch (index) {
                case "0" -> EdgeSearchProfile.YOLO;
                case "1" -> EdgeSearchProfile.MODERN;
                case "2" -> EdgeSearchProfile.DEFAULT;
                case "3" -> EdgeSearchProfile.CORPO_CLEAN;
                default -> EdgeSearchProfile.CORPO_CLEAN;
            };
        }

        final String humanQuery = queryParam.trim();

        var results = searchOperator.doApiSearch(ctx, new EdgeUserSearchParameters(humanQuery, profile, SearchJsParameter.DEFAULT));

        return new ApiSearchResults("RESTRICTED", humanQuery, results.stream().map(ApiSearchResult::new).limit(limit).collect(Collectors.toList()));
    }

}
