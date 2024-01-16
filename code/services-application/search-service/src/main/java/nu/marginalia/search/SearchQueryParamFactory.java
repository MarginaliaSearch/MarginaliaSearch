package nu.marginalia.search;

import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.query.model.QueryParams;
import nu.marginalia.search.command.SearchParameters;

import java.util.List;

public class SearchQueryParamFactory {

    public QueryParams forRegularSearch(SearchParameters userParams) {
        SearchSubquery prototype =  new SearchSubquery();
        var profile = userParams.profile();

        profile.addTacitTerms(prototype);
        userParams.js().addTacitTerms(prototype);
        userParams.adtech().addTacitTerms(prototype);

        SpecificationLimit yearLimit = userParams.recent().yearLimit();

        return new QueryParams(
                userParams.query(),
                null,
                prototype.searchTermsInclude,
                prototype.searchTermsExclude,
                prototype.searchTermsPriority,
                prototype.searchTermsAdvice,
                profile.getQualityLimit(),
                yearLimit,
                profile.getSizeLimit(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                List.of(),
                new QueryLimits(1, 25, 200, 8192),
                profile.searchSetIdentifier.name()
        );

    }

    public QueryParams forSiteSearch(String domain, int count) {
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
                SpecificationLimit.none(),
                List.of(),
                new QueryLimits(count, count, 100, 512),
                SearchSetIdentifier.NONE.name()
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
                SpecificationLimit.none(),
                List.of(),
                new QueryLimits(100, 100, 100, 512),
                SearchSetIdentifier.NONE.name()
        );
    }

    public QueryParams forLinkSearch(String sourceDomain, String destDomain) {
        return new QueryParams(STR."site:\{sourceDomain} links:\{destDomain}",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                SpecificationLimit.none(),
                List.of(),
                new QueryLimits(100, 100, 100, 512),
                SearchSetIdentifier.NONE.name()
        );
    }
}
