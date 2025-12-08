package nu.marginalia.search.model;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static nu.marginalia.api.searchquery.QueryFilterSpec.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchParametersTest {
    private final WebsiteUrl websiteUrl = new WebsiteUrl("https://www.example.com/");
    private final String systemUser = "SYSTEM";
    private final String defaultFilter = SearchFilterDefaults.NO_FILTER.name();
    
    @Test
    void asFilterSpec__default() {
        QueryFilterSpec spec = SearchParameters.defaultsForQuery(websiteUrl, "test", 1).asFilterSpec();
        Assertions.assertEquals(new FilterByName(systemUser, defaultFilter), spec);
    }

    @Test
    void asFilterSpec__blogs() {
        QueryFilterSpec spec = SearchParameters.defaultsForQuery(websiteUrl, "test", 1)
                .withProfile(SearchProfile.BLOGOSPHERE)
                .asFilterSpec();

        Assertions.assertEquals(new FilterByName(systemUser, SearchFilterDefaults.BLOGOSPHERE.name()), spec);
    }

    @Test
    void asFilterSpec__all_profiles() {
        for (var profile : SearchProfile.values()) {
            QueryFilterSpec spec = SearchParameters.defaultsForQuery(websiteUrl, "test", 1)
                    .withProfile(profile)
                    .asFilterSpec();

            Assertions.assertEquals(new FilterByName(systemUser, profile.defaultFilter.name()), spec);
        }
    }

    @Test
    void asFilterSpec__no_js() {
        QueryFilterSpec spec = SearchParameters.defaultsForQuery(websiteUrl, "test", 1)
                .withJs(SearchJsParameter.DENY_JS)
                .asFilterSpec();

        var expectedAdHocFilter = FilterAdHoc.builder()
                .termsExclude(List.of("special:scripts"))
                .build();

        Assertions.assertEquals(new CombinedFilter(new FilterByName(systemUser, defaultFilter), expectedAdHocFilter), spec);
    }

    @Test
    void asFilterSpec__no_ads() {
        QueryFilterSpec spec = SearchParameters.defaultsForQuery(websiteUrl, "test", 1)
                .withAdtech(SearchAdtechParameter.REDUCE)
                .asFilterSpec();

        var expectedAdHocFilter = FilterAdHoc.builder()
                .termsExclude(List.of("special:ads", "special:affiliate"))
                .build();

        Assertions.assertEquals(new CombinedFilter(new FilterByName(systemUser, defaultFilter), expectedAdHocFilter), spec);
    }
    @Test
    void asFilterSpec__recent() {
        QueryFilterSpec spec = SearchParameters.defaultsForQuery(websiteUrl, "test", 1)
                .withRecent(SearchRecentParameter.RECENT)
                .asFilterSpec();

        var expectedAdHocFilter = FilterAdHoc.builder()
                .temporalBias(RpcTemporalBias.Bias.RECENT)
                .build();

        Assertions.assertEquals(new CombinedFilter(new FilterByName(systemUser, defaultFilter), expectedAdHocFilter), spec);

    }

    @Test
    void asFilterSpec__title() {
        QueryFilterSpec spec = SearchParameters.defaultsForQuery(websiteUrl, "test", 1)
                .withTitle(SearchTitleParameter.TITLE)
                .asFilterSpec();

        var expectedAdHocFilter = FilterAdHoc.builder()
                .queryStrategy(QueryStrategy.REQUIRE_FIELD_TITLE)
                .build();


        Assertions.assertEquals(new CombinedFilter(new FilterByName(systemUser, defaultFilter), expectedAdHocFilter), spec);

    }

    @Test
    void asFilterSpec__combined_flags() {
        QueryFilterSpec spec = SearchParameters.defaultsForQuery(websiteUrl, "test", 1)
                .withTitle(SearchTitleParameter.TITLE)
                .withAdtech(SearchAdtechParameter.REDUCE)
                .withRecent(SearchRecentParameter.RECENT)
                .withJs(SearchJsParameter.DENY_JS)
                .asFilterSpec();

        var expectedAdHocFilter = FilterAdHoc.builder()
                .queryStrategy(QueryStrategy.REQUIRE_FIELD_TITLE)
                .temporalBias(RpcTemporalBias.Bias.RECENT)
                .termsExclude(List.of("special:scripts", "special:ads", "special:affiliate"))
                .build();


        Assertions.assertEquals(new CombinedFilter(new FilterByName(systemUser, defaultFilter), expectedAdHocFilter), spec);
    }
}