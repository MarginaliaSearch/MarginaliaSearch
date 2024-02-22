package nu.marginalia.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.math.MathClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.query.client.QueryClient;
import nu.marginalia.query.model.QueryResponse;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.*;
import nu.marginalia.search.svc.SearchQueryIndexService;
import nu.marginalia.search.svc.SearchUnitConversionService;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class SearchOperator {

    private static final Logger logger = LoggerFactory.getLogger(SearchOperator.class);

    // Marker for filtering out sensitive content from the persistent logs
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private final MathClient mathClient;
    private final DbDomainQueries domainQueries;
    private final QueryClient queryClient;
    private final SearchQueryIndexService searchQueryService;
    private final SearchQueryParamFactory paramFactory;
    private final WebsiteUrl websiteUrl;
    private final SearchUnitConversionService searchUnitConversionService;


    @Inject
    public SearchOperator(MathClient mathClient,
                          DbDomainQueries domainQueries,
                          QueryClient queryClient,
                          SearchQueryIndexService searchQueryService,
                          SearchQueryParamFactory paramFactory,
                          WebsiteUrl websiteUrl,
                          SearchUnitConversionService searchUnitConversionService)
    {

        this.mathClient = mathClient;
        this.domainQueries = domainQueries;
        this.queryClient = queryClient;

        this.searchQueryService = searchQueryService;
        this.paramFactory = paramFactory;
        this.websiteUrl = websiteUrl;
        this.searchUnitConversionService = searchUnitConversionService;
    }

    public List<UrlDetails> doSiteSearch(String domain,
                                        int count) {

        var queryParams = paramFactory.forSiteSearch(domain, count);
        var queryResponse = queryClient.search(queryParams);

        return searchQueryService.getResultsFromQuery(queryResponse);
    }

    public List<UrlDetails> doBacklinkSearch(String domain) {

        var queryParams = paramFactory.forBacklinkSearch(domain);
        var queryResponse = queryClient.search(queryParams);

        return searchQueryService.getResultsFromQuery(queryResponse);
    }

    public List<UrlDetails> doLinkSearch(String source, String dest) {
        var queryParams = paramFactory.forLinkSearch(source, dest);
        var queryResponse = queryClient.search(queryParams);

        return searchQueryService.getResultsFromQuery(queryResponse);
    }

    public DecoratedSearchResults doSearch(SearchParameters userParams) {

        Future<String> eval = searchUnitConversionService.tryEval(userParams.query());
        var queryParams = paramFactory.forRegularSearch(userParams);
        var queryResponse = queryClient.search(queryParams);

        List<UrlDetails> queryResults = searchQueryService.getResultsFromQuery(queryResponse);

        logger.info(queryMarker, "Human terms: {}", Strings.join(queryResponse.searchTermsHuman(), ','));
        logger.info(queryMarker, "Search Result Count: {}", queryResults.size());

        String evalResult = getFutureOrDefault(eval, "");

        List<ClusteredUrlDetails> clusteredResults = SearchResultClusterer
                .selectStrategy(queryResponse)
                .clusterResults(queryResults, 25);

        return DecoratedSearchResults.builder()
                .params(userParams)
                .problems(getProblems(evalResult, queryResults, queryResponse))
                .evalResult(evalResult)
                .results(clusteredResults)
                .filters(new SearchFilters(websiteUrl, userParams))
                .focusDomain(queryResponse.domain())
                .focusDomainId(getDomainId(queryResponse.domain()))
                .build();
    }

    private <T> T getFutureOrDefault(@Nullable Future<T> fut, T defaultValue) {
        if (fut == null || fut.isCancelled())  {
            return defaultValue;
        }
        try {
            return fut.get(50, TimeUnit.MILLISECONDS);
        }
        catch (Exception ex) {
            logger.warn("Error fetching eval result", ex);
            return defaultValue;
        }
    }

    private int getDomainId(String domain) {
        if (domain == null) {
            return -1;
        }

        return domainQueries.tryGetDomainId(new EdgeDomain(domain)).orElse(-1);
    }

    private List<String> getProblems(String evalResult, List<UrlDetails> queryResults, QueryResponse response) {
        final List<String> problems = new ArrayList<>(response.problems());
        boolean siteSearch = response.domain() != null;

        if (!siteSearch) {
            if (queryResults.size() <= 5 && null == evalResult) {
                spellCheckTerms(response);
            }

            if (queryResults.size() <= 5) {
                problems.add("Try rephrasing the query, changing the word order or using synonyms to get different results. <a href=\"https://memex.marginalia.nu/projects/edge/search-tips.gmi\">Tips</a>.");
            }

            Set<String> representativeKeywords = response.getAllKeywords();
            if (representativeKeywords.size()>1 && (representativeKeywords.contains("definition") || representativeKeywords.contains("define") || representativeKeywords.contains("meaning")))
            {
                problems.add("Tip: Try using a query that looks like <tt>define:word</tt> if you want a dictionary definition");
            }
        }

        return problems;
    }


    @SneakyThrows
    private void spellCheckTerms(QueryResponse response) {
        var suggestions = mathClient
                .spellCheck(response.searchTermsHuman(), Duration.ofMillis(20));

        suggestions.entrySet()
                .stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> searchTermToProblemDescription(e.getKey(), e.getValue()))
                .forEach(response.problems()::add);
    }

    private String searchTermToProblemDescription(String term, List<String> suggestions) {
        String suggestionsStr = suggestions.stream().map(s -> STR."\"\{s}\"").collect(Collectors.joining(", "));

        return STR."\"\{term}\" could be spelled \{suggestionsStr}";
    }



}
