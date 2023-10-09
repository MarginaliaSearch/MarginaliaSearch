package nu.marginalia.search;

import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.query.model.QueryParams;
import nu.marginalia.search.model.UserSearchParameters;

import java.util.List;

public class SearchQueryParamFactory {

    public QueryParams forRegularSearch(UserSearchParameters userParams) {
        SearchSubquery prototype =  new SearchSubquery();
        var profile = userParams.profile();
        profile.addTacitTerms(prototype);

        return new QueryParams(
                userParams.humanQuery(),
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
                new QueryLimits(2, 100, 200, 8192),
                profile.searchSetIdentifier
        );

    }

    public QueryParams forSiteSearch(String domain) {
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
                List.of(),
                new QueryLimits(100, 100, 100, 512),
                SearchSetIdentifier.NONE
        );
    }
}
