package nu.marginalia.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.model.ApiSearchResult;
import nu.marginalia.api.model.ApiSearchResultQueryDetails;
import nu.marginalia.api.model.ApiSearchResults;
import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import nu.marginalia.index.client.model.results.DecoratedSearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.query.client.QueryClient;
import nu.marginalia.query.model.QueryParams;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ApiSearchOperator {
    private final QueryClient queryClient;

    @Inject
    public ApiSearchOperator(QueryClient queryClient) {
        this.queryClient = queryClient;
    }

    public ApiSearchResults query(String query,
                                  int count,
                                  int index)
    {
        var rsp = queryClient.search(createParams(query, count, index));

        return new ApiSearchResults("RESTRICTED", query,
                rsp.results()
                .stream()
                .map(this::convert)
                .sorted(Comparator.comparing(ApiSearchResult::getQuality))
                .limit(count)
                .collect(Collectors.toList()));
    }

    private QueryParams createParams(String query, int count, int index) {
        SearchSetIdentifier searchSet = selectSearchSet(index);

        return new QueryParams(
                query,
                new QueryLimits(
                        2,
                        Math.min(100, count),
                        150,
                        8192),
                searchSet.name());
    }

    private SearchSetIdentifier selectSearchSet(int index) {
        return switch (index) {
            case 0 -> SearchSetIdentifier.NONE;
            case 1 -> SearchSetIdentifier.SMALLWEB;
            case 2 -> SearchSetIdentifier.POPULAR;
            case 3 -> SearchSetIdentifier.NONE;
            case 5 -> SearchSetIdentifier.NONE;
            default -> SearchSetIdentifier.NONE;
        };
    }



    ApiSearchResult convert(DecoratedSearchResultItem url) {
        List<List<ApiSearchResultQueryDetails>> details = new ArrayList<>();
        if (url.rawIndexResult != null) {
            var bySet = url.rawIndexResult.keywordScores.stream().collect(Collectors.groupingBy(SearchResultKeywordScore::subquery));

            outer:
            for (var entries : bySet.values()) {
                List<ApiSearchResultQueryDetails> lst = new ArrayList<>();
                for (var entry : entries) {
                    var metadata = new WordMetadata(entry.encodedWordMetadata());
                    if (metadata.isEmpty())
                        continue outer;

                    Set<String> flags = metadata.flagSet().stream().map(Object::toString).collect(Collectors.toSet());
                    lst.add(new ApiSearchResultQueryDetails(entry.keyword, Long.bitCount(metadata.positions()), flags));
                }
                details.add(lst);
            }
        }

        return new ApiSearchResult(
                url.url.toString(),
                url.getTitle(),
                url.getDescription(),
                sanitizeNaN(url.rankingScore, -100),
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
