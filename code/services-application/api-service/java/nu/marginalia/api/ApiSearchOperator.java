package nu.marginalia.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.model.ApiSearchResult;
import nu.marginalia.api.model.ApiSearchResultQueryDetails;
import nu.marginalia.api.model.ApiSearchResults;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.SearchSetIdentifier;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.model.idx.WordFlags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
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
                                  int domainCount,
                                  int index,
                                  NsfwFilterTier filterTier,
                                  String langIsoCode)
            throws TimeoutException
    {
        var rsp = queryClient.search(createParams(query, count, domainCount, index, filterTier, langIsoCode));

        return new ApiSearchResults("RESTRICTED", query,
                rsp.results()
                .stream()
                .map(this::convert)
                .sorted(Comparator.comparing(ApiSearchResult::getQuality))
                .limit(count)
                .collect(Collectors.toList()));
    }

    private QueryParams createParams(String query,
                                     int count,
                                     int domainCount,
                                     int index,
                                     NsfwFilterTier filterTirer,
                                     String langIsoCode) {
        SearchSetIdentifier searchSet = selectSearchSet(index);

        return new QueryParams(
                query,
                RpcQueryLimits.newBuilder()
                        .setResultsByDomain(Math.clamp(domainCount, 1, 100))
                        .setResultsTotal(Math.min(100, count))
                        .setTimeoutMs(150)
                        .setFetchSize(8192)
                        .build(),
                searchSet.name(),
                filterTirer,
                langIsoCode);
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

        // This list-of-list construction is to avoid breaking the API,
        // we'll always have just a single outer list from now on...

        if (url.rawIndexResult != null) {
            List<ApiSearchResultQueryDetails> lst = new ArrayList<>();
            for (var entry : url.rawIndexResult.keywordScores) {
                Set<String> flags = WordFlags.decode(entry.flags).stream().map(Object::toString).collect(Collectors.toSet());
                lst.add(new ApiSearchResultQueryDetails(entry.keyword, entry.positionCount, flags));
            }

            details.add(lst);
        }

        return new ApiSearchResult(
                url.url.toString(),
                url.getTitle(),
                url.getDescription(),
                sanitizeNaN(url.rankingScore, -100),
                url.getShortFormat(),
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
