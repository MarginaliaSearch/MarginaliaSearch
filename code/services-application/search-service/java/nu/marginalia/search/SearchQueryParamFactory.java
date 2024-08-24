package nu.marginalia.search;

import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.query.SearchSetIdentifier;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.search.command.SearchParameters;

import java.util.List;

public class SearchQueryParamFactory {

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
                profile.getYearLimit(),
                profile.getSizeLimit(),
                SpecificationLimit.none(),
                List.of(),
                new QueryLimits(5, 100, 200, 8192),
                profile.searchSetIdentifier.name(),
                userParams.strategy(),
                userParams.temporalBias()
        );

    }

    public QueryParams forSiteSearch(String domain, int domainId, int count) {
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
                new QueryLimits(count, count, 100, 512),
                SearchSetIdentifier.NONE.name(),
                QueryStrategy.AUTO,
                ResultRankingParameters.TemporalBias.NONE
        );
    }

    public QueryParams forBacklinkSearch(String domain) {
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
                new QueryLimits(100, 100, 100, 512),
                SearchSetIdentifier.NONE.name(),
                QueryStrategy.AUTO,
                ResultRankingParameters.TemporalBias.NONE
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
                new QueryLimits(100, 100, 100, 512),
                SearchSetIdentifier.NONE.name(),
                QueryStrategy.AUTO,
                ResultRankingParameters.TemporalBias.NONE
        );
    }
}
