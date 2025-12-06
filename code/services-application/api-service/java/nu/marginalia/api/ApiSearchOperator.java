package nu.marginalia.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.api.model.ApiSearchResult;
import nu.marginalia.api.model.ApiSearchResultQueryDetails;
import nu.marginalia.api.model.ApiSearchResults;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
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

    public ApiSearchResults v2query(String query,
                                    int count,
                                    int domainCount,
                                    QueryFilterSpec filterSpec,
                                    NsfwFilterTier filterTier,
                                    String langIsoCode,
                                    ApiLicense license)
            throws TimeoutException
    {


        var rsp = queryClient.search(
                filterSpec,
                query,
                langIsoCode,
                filterTier,
                RpcQueryLimits.newBuilder()
                        .setResultsByDomain(Math.clamp(domainCount, 1, 100))
                        .setResultsTotal(Math.min(100, count))
                        .setTimeoutMs(150)
                        .build(),
                1);

        return new ApiSearchResults(license.getLicense(), query,
                rsp.results()
                        .stream()
                        .map(this::convert)
                        .sorted(Comparator.comparing(ApiSearchResult::getQuality))
                        .limit(count)
                        .collect(Collectors.toList()));
    }


    public ApiSearchResults v1query(String query,
                                    int count,
                                    int domainCount,
                                    int index,
                                    NsfwFilterTier filterTier,
                                    String langIsoCode,
                                    ApiLicense license)
            throws TimeoutException
    {


        var rsp = queryClient.search(
                selectFilter(index).asFilterSpec(),
                query,
                langIsoCode,
                filterTier,
                RpcQueryLimits.newBuilder()
                        .setResultsByDomain(Math.clamp(domainCount, 1, 100))
                        .setResultsTotal(Math.min(100, count))
                        .setTimeoutMs(150)
                        .build(),
                1);

        return new ApiSearchResults(license.getLicense(), query,
                rsp.results()
                .stream()
                .map(this::convert)
                .sorted(Comparator.comparing(ApiSearchResult::getQuality))
                .limit(count)
                .collect(Collectors.toList()));
    }

    private SearchFilterDefaults selectFilter(int index) {
        return switch (index) {
            case 0 -> SearchFilterDefaults.NO_FILTER;
            case 1 -> SearchFilterDefaults.SMALLWEB;
            case 2 -> SearchFilterDefaults.POPULAR;
            default -> SearchFilterDefaults.NO_FILTER;
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
