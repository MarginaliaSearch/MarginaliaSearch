package nu.marginalia.search;

import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.query.SearchSetIdentifier;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.search.command.SearchParameters;

import java.util.List;

public class SearchQueryParamFactory {


    static final RpcQueryLimits defaultLimits = RpcQueryLimits.newBuilder()
            .setResultsTotal(100)
            .setResultsByDomain(5)
            .setTimeoutMs(250)
            .setFetchSize(8192)
            .build();


    static final RpcQueryLimits shallowLimit = RpcQueryLimits.newBuilder()
            .setResultsTotal(100)
            .setResultsByDomain(100)
            .setTimeoutMs(100)
            .setFetchSize(512)
            .build();

    public QueryParams forRegularSearch(SearchParameters userParams) {
        SearchQuery prototype =  new SearchQuery();
        var profile = userParams.profile();

        profile.addTacitTerms(prototype);
        userParams.js().addTacitTerms(prototype);
        userParams.adtech().addTacitTerms(prototype);

        return new QueryParams(
                userParams.query(),
                null,
                prototype.searchTermsInclude,
                prototype.searchTermsExclude,
                prototype.searchTermsPriority,
                prototype.searchTermsAdvice,
                profile.getQualityLimit(),
                userParams.yearLimit(),
                profile.getSizeLimit(),
                SpecificationLimit.none(),
                List.of(),
                defaultLimits,
                profile.searchSetIdentifier.name(),
                userParams.strategy(),
                userParams.temporalBias(),
                userParams.filterTier(),
                userParams.page()
        );

    }

    public QueryParams forSiteSearch(String domain, int domainId, int count, int page) {
        return new QueryParams("site:"+domain,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                List.of(domainId),
                RpcQueryLimits.newBuilder()
                        .setResultsTotal(count)
                        .setResultsByDomain(count)
                        .setTimeoutMs(100)
                        .setFetchSize(512)
                        .build(),
                SearchSetIdentifier.NONE.name(),
                QueryStrategy.AUTO,
                RpcTemporalBias.Bias.NONE,
                NsfwFilterTier.OFF,
                page
        );
    }

    public QueryParams forBacklinkSearch(String domain, int page) {
        return new QueryParams("links:"+domain,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                List.of(),
                shallowLimit,
                SearchSetIdentifier.NONE.name(),
                QueryStrategy.AUTO,
                RpcTemporalBias.Bias.NONE,
                NsfwFilterTier.DANGER,
                page
        );
    }

    public QueryParams forLinkSearch(String sourceDomain, String destDomain) {
        return new QueryParams("site:" + sourceDomain + " links:" + destDomain,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                List.of(),
                shallowLimit,
                SearchSetIdentifier.NONE.name(),
                QueryStrategy.AUTO,
                RpcTemporalBias.Bias.NONE,
                NsfwFilterTier.DANGER,
                1
        );
    }
}
