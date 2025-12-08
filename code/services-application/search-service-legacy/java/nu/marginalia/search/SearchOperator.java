package nu.marginalia.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.math.MathClient;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryResponse;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.bbpc.BrailleBlockPunchCards;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Singleton
public class SearchOperator {

    private static final Logger logger = LoggerFactory.getLogger(SearchOperator.class);

    // Marker for filtering out sensitive content from the persistent logs
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private final MathClient mathClient;
    private final DbDomainQueries domainQueries;
    private final QueryClient queryClient;
    private final WebsiteUrl websiteUrl;
    private final SearchUnitConversionService searchUnitConversionService;
    private final SearchQueryCountService searchVisitorCount;

    static final RpcQueryLimits shallowLimit = RpcQueryLimits.newBuilder()
            .setResultsTotal(100)
            .setResultsByDomain(100)
            .setTimeoutMs(100)
            .build();

    static final RpcQueryLimits defaultLimits = RpcQueryLimits.newBuilder()
            .setResultsTotal(100)
            .setResultsByDomain(5)
            .setTimeoutMs(250)
            .build();

    @Inject
    public SearchOperator(MathClient mathClient,
                          DbDomainQueries domainQueries,
                          QueryClient queryClient,
                          WebsiteUrl websiteUrl,
                          SearchUnitConversionService searchUnitConversionService,
                          SearchQueryCountService searchVisitorCount
                          )
    {

        this.mathClient = mathClient;
        this.domainQueries = domainQueries;
        this.queryClient = queryClient;
        this.websiteUrl = websiteUrl;
        this.searchUnitConversionService = searchUnitConversionService;
        this.searchVisitorCount = searchVisitorCount;
    }

    public List<UrlDetails> doSiteSearch(String domain,
                                        int domainId,
                                        int count) throws TimeoutException {

        var queryResponse = queryClient.search(
                QueryFilterSpec.FilterAdHoc.builder().domainsInclude(IntList.of(domainId)).build(),
                "site:"+domain,
                "en",
                NsfwFilterTier.DANGER,
                RpcQueryLimits.newBuilder()
                        .setResultsTotal(count)
                        .setResultsByDomain(count)
                        .setTimeoutMs(100)
                        .build(),
                1
        );


        return getResultsFromQuery(queryResponse);
    }

    public List<UrlDetails> doBacklinkSearch(String domain) throws TimeoutException {

        var queryResponse = queryClient.search(
                new QueryFilterSpec.NoFilter(),
                "links:"+domain,
                "en",
                NsfwFilterTier.DANGER,
                shallowLimit,
                1
        );

        return getResultsFromQuery(queryResponse);
    }

    public List<UrlDetails> doLinkSearch(String source, String dest) throws TimeoutException {
        var queryResponse = queryClient.search(
                new QueryFilterSpec.NoFilter(),
                "site:" + source + " links:" + dest,
                "en",
                NsfwFilterTier.DANGER,
                shallowLimit,
                1
        );

        return getResultsFromQuery(queryResponse);
    }

    public DecoratedSearchResults doSearch(SearchParameters userParams) throws InterruptedException, TimeoutException {
        // The full user-facing search query does additional work to try to evaluate the query
        // e.g. as a unit conversion query. This is done in parallel with the regular search.

        Future<String> eval = searchUnitConversionService.tryEval(userParams.query());

        // Perform the regular search

        QueryResponse queryResponse = queryClient.search(
                userParams.asFilterSpec(),
                userParams.query(),
                "en",
                userParams.filterTier(),
                defaultLimits,
                userParams.page()
        );

        var queryResults = getResultsFromQuery(queryResponse);

        // Cluster the results based on the query response
        List<ClusteredUrlDetails> clusteredResults = SearchResultClusterer
                .selectStrategy(queryResponse)
                .clusterResults(queryResults, 25);

        // Log the query and results

        logger.info(queryMarker, "Human terms: {}", Strings.join(queryResponse.searchTermsHuman(), ','));
        logger.info(queryMarker, "Search Result Count: {}", queryResults.size());

        // Get the evaluation result and other data to return to the user
        String evalResult = getFutureOrDefault(eval, "");

        String focusDomain = queryResponse.domain();
        int focusDomainId = focusDomain == null
                ? -1
                : domainQueries.tryGetDomainId(new EdgeDomain(focusDomain)).orElse(-1);

        List<String> problems = getProblems(evalResult, queryResults, queryResponse);

        List<DecoratedSearchResults.Page> resultPages = IntStream.rangeClosed(1, queryResponse.totalPages())
                .mapToObj(number -> new DecoratedSearchResults.Page(
                        number,
                        number == userParams.page(),
                        userParams.withPage(number).renderUrl(websiteUrl)
                ))
                .toList();

        // Return the results to the user
        return DecoratedSearchResults.builder()
                .params(userParams)
                .problems(problems)
                .evalResult(evalResult)
                .results(clusteredResults)
                .filters(new SearchFilters(websiteUrl, userParams))
                .focusDomain(focusDomain)
                .focusDomainId(focusDomainId)
                .resultPages(resultPages)
                .build();
    }


    public List<UrlDetails> getResultsFromQuery(QueryResponse queryResponse) {
        final RpcQueryLimits limits = queryResponse.limits();
        final UrlDeduplicator deduplicator = new UrlDeduplicator(limits.getResultsByDomain());

        // Update the query count (this is what you see on the front page)
        searchVisitorCount.registerQuery();

        return queryResponse.results().stream()
                .filter(deduplicator::shouldRetain)
                .limit(limits.getResultsTotal())
                .map(SearchOperator::createDetails)
                .toList();
    }

    private static UrlDetails createDetails(DecoratedSearchResultItem item) {
        return new UrlDetails(
                item.documentId(),
                item.domainId(),
                cleanUrl(item.url),
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

    /** Replace nuisance domains with replacements where available */
    private static EdgeUrl cleanUrl(EdgeUrl url) {
        String topdomain = url.domain.topDomain;
        String subdomain = url.domain.subDomain;
        String path = url.path;

        if (topdomain.equals("fandom.com")) {
            int wikiIndex = path.indexOf("/wiki/");
            if (wikiIndex >= 0) {
                return new EdgeUrl("https", new EdgeDomain("breezewiki.com"), null,  "/" + subdomain + path.substring(wikiIndex), null);
            }
        }
        /*
        else if (topdomain.equals("medium.com")) {
            int slashIndex = path.indexOf("/", 1);
            if (slashIndex >= 0) {
                if (!subdomain.isBlank()) {
                    return new EdgeUrl("https", new EdgeDomain("scribe.rip"), null, path, null);
                }
                else {
                    String article = path.substring(slashIndex);
                    return new EdgeUrl("https", new EdgeDomain("scribe.rip"), null, article, null);
                }
            }
        }*/
        return url;
    }

    private List<String> getProblems(String evalResult, List<UrlDetails> queryResults, QueryResponse response) throws InterruptedException {

        // We don't debug the query if it's a site search
        if (response.domain() == null)
            return List.of();

        final List<String> problems = new ArrayList<>(response.problems());

        if (queryResults.size() <= 5 && null == evalResult) {
            problems.add("Try rephrasing the query, changing the word order or using synonyms to get different results.");

            // Try to spell check the search terms
            var suggestions = getFutureOrDefault(
                    mathClient.spellCheck(response.searchTermsHuman()),
                    Map.of()
            );

            suggestions.forEach((term, suggestion) -> {
                if (suggestion.size() > 1) {
                    String suggestionsStr = "\"%s\" could be spelled %s".formatted(term, suggestion.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")));
                    problems.add(suggestionsStr);
                }
            });
        }

        Set<String> representativeKeywords = response.getAllKeywords();
        if (representativeKeywords.size() > 1 && (representativeKeywords.contains("definition") || representativeKeywords.contains("define") || representativeKeywords.contains("meaning")))
        {
            problems.add("Tip: Try using a query that looks like <tt>define:word</tt> if you want a dictionary definition");
        }

        return problems;
    }

    private <T> T getFutureOrDefault(@Nullable Future<T> fut, T defaultValue) {
        return getFutureOrDefault(fut, Duration.ofMillis(50), defaultValue);
    }

    private <T> T getFutureOrDefault(@Nullable Future<T> fut, Duration timeout, T defaultValue) {
        if (fut == null || fut.isCancelled())  {
            return defaultValue;
        }
        try {
            return fut.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (Exception ex) {
            logger.warn("Error fetching eval result", ex);
            return defaultValue;
        }
    }

}
