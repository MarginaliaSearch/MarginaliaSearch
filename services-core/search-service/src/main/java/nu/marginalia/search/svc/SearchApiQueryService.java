package nu.marginalia.search.svc;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.index.client.model.results.EdgeSearchResultKeywordScore;
import nu.marginalia.search.client.model.ApiSearchResultQueryDetails;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.search.SearchOperator;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.client.model.ApiSearchResult;
import nu.marginalia.search.client.model.ApiSearchResults;
import nu.marginalia.search.model.SearchProfile;
import nu.marginalia.client.Context;
import nu.marginalia.search.command.SearchJsParameter;
import nu.marginalia.search.query.model.UserSearchParameters;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SearchApiQueryService {
    private SearchOperator searchOperator;

    @Inject
    public SearchApiQueryService(SearchOperator searchOperator) {
        this.searchOperator = searchOperator;
    }

    @SneakyThrows
    public Object apiSearch(Request request, Response response) {

        final var ctx = Context.fromRequest(request);
        final String queryParam = request.queryParams("query");
        final int limit;
        SearchProfile profile = SearchProfile.YOLO;

        String count = request.queryParamOrDefault("count", "20");
        limit = Integer.parseInt(count);

        String index = request.queryParamOrDefault("index", "0");
        if (!Strings.isNullOrEmpty(index)) {
            profile = switch (index) {
                case "0" -> SearchProfile.YOLO;
                case "1" -> SearchProfile.MODERN;
                case "2" -> SearchProfile.DEFAULT;
                case "3" -> SearchProfile.CORPO_CLEAN;
                default -> SearchProfile.CORPO_CLEAN;
            };
        }

        final String humanQuery = queryParam.trim();

        var results = searchOperator.doApiSearch(ctx, new UserSearchParameters(humanQuery, profile, SearchJsParameter.DEFAULT));

        return new ApiSearchResults("RESTRICTED", humanQuery, results.stream().map(this::convert).limit(limit).collect(Collectors.toList()));
    }

    ApiSearchResult convert(UrlDetails url) {
        List<List<ApiSearchResultQueryDetails>> details = new ArrayList<>();
        if (url.resultItem != null) {
            var bySet = url.resultItem.scores.stream().collect(Collectors.groupingBy(EdgeSearchResultKeywordScore::set));

            outer:
            for (var entries : bySet.values()) {
                List<ApiSearchResultQueryDetails> lst = new ArrayList<>();
                for (var entry : entries) {
                    var metadata = new WordMetadata(entry.encodedWordMetadata());
                    if (metadata.isEmpty())
                        continue outer;

                    Set<String> flags = metadata.flagSet().stream().map(Object::toString).collect(Collectors.toSet());
                    lst.add(new ApiSearchResultQueryDetails(entry.keyword(), metadata.tfIdf(), metadata.count(), flags));
                }
                details.add(lst);
            }
        }

        return new ApiSearchResult(
                url.url.toString(),
                url.getTitle(),
                url.getDescription(),
                sanitizeNaN(url.getTermScore(), -100),
                details
        );
    }

    private double sanitizeNaN(double value, double alternative) {
        if (!Double.isFinite(value)) {
            return alternative;
        }
        return value;
    }

}
