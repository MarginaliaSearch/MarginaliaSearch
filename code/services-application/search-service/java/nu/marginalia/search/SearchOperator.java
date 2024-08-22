package nu.marginalia.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.math.MathClient;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.model.query.QueryResponse;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.bbpc.BrailleBlockPunchCards;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.ClusteredUrlDetails;
import nu.marginalia.search.model.DecoratedSearchResults;
import nu.marginalia.search.model.SearchFilters;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.search.results.UrlDeduplicator;
import nu.marginalia.search.svc.SearchQueryCountService;
import nu.marginalia.search.svc.SearchUnitConversionService;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private final SearchQueryParamFactory paramFactory;
    private final WebsiteUrl websiteUrl;
    private final SearchUnitConversionService searchUnitConversionService;
    private final SearchQueryCountService searchVisitorCount;


    @Inject
    public SearchOperator(MathClient mathClient,
                          DbDomainQueries domainQueries,
                          QueryClient queryClient,
                          SearchQueryParamFactory paramFactory,
                          WebsiteUrl websiteUrl,
                          SearchUnitConversionService searchUnitConversionService,
                          SearchQueryCountService searchVisitorCount
                          )
    {

        this.mathClient = mathClient;
        this.domainQueries = domainQueries;
        this.queryClient = queryClient;
        this.paramFactory = paramFactory;
        this.websiteUrl = websiteUrl;
        this.searchUnitConversionService = searchUnitConversionService;
        this.searchVisitorCount = searchVisitorCount;
    }

    public List<UrlDetails> doSiteSearch(String domain,
                                        int domainId,
                                        int count) {

        var queryParams = paramFactory.forSiteSearch(domain, domainId, count);
        var queryResponse = queryClient.search(queryParams);

        return getResultsFromQuery(queryResponse);
    }

    public List<UrlDetails> doBacklinkSearch(String domain) {

        var queryParams = paramFactory.forBacklinkSearch(domain);
        var queryResponse = queryClient.search(queryParams);

        return getResultsFromQuery(queryResponse);
    }

    public List<UrlDetails> doLinkSearch(String source, String dest) {
        var queryParams = paramFactory.forLinkSearch(source, dest);
        var queryResponse = queryClient.search(queryParams);

        return getResultsFromQuery(queryResponse);
    }

    public DecoratedSearchResults doSearch(SearchParameters userParams) {

        Future<String> eval = searchUnitConversionService.tryEval(userParams.query());

        var queryParams = paramFactory.forRegularSearch(userParams);
        QueryResponse queryResponse = queryClient.search(queryParams);
        var queryResults = getResultsFromQuery(queryResponse);

        logger.info(queryMarker, "Human terms: {}", Strings.join(queryResponse.searchTermsHuman(), ','));
        logger.info(queryMarker, "Search Result Count: {}", queryResults.size());

        String evalResult = getFutureOrDefault(eval, "");

        List<ClusteredUrlDetails> clusteredResults = SearchResultClusterer
                .selectStrategy(queryResponse)
                .clusterResults(queryResults, 25);

        String focusDomain = queryResponse.domain();
        List<String> problems = getProblems(evalResult, queryResults, queryResponse);

        return DecoratedSearchResults.builder()
                .params(userParams)
                .problems(problems)
                .evalResult(evalResult)
                .results(clusteredResults)
                .filters(new SearchFilters(websiteUrl, userParams))
                .focusDomain(focusDomain)
                .focusDomainId(getDomainId(focusDomain))
                .build();
    }


    public List<UrlDetails> getResultsFromQuery(QueryResponse queryResponse) {
        final QueryLimits limits = queryResponse.specs().queryLimits;
        final UrlDeduplicator deduplicator = new UrlDeduplicator(limits.resultsByDomain());

        // Update the query count (this is what you see on the front page)
        searchVisitorCount.registerQuery();

        return queryResponse.results().stream()
                .filter(deduplicator::shouldRetain)
                .limit(limits.resultsTotal())
                .map(SearchOperator::createDetails)
                .toList();
    }

    private static UrlDetails createDetails(DecoratedSearchResultItem item) {
        return new UrlDetails(
                item.documentId(),
                item.domainId(),
                item.url,
                item.title,
                item.description,
                item.format,
                item.features,
                DomainIndexingState.ACTIVE,
                item.rankingScore, // termScore
                item.resultsFromDomain,
                BrailleBlockPunchCards.printBits(item.bestPositions, 64),
                Long.bitCount(item.bestPositions),
                item.rawIndexResult,
                item.rawIndexResult.keywordScores
        );
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
