package nu.marginalia.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.assistant.client.AssistantClient;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.dbcommon.DbDomainQueries;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.client.Context;
import nu.marginalia.search.model.DecoratedSearchResults;
import nu.marginalia.search.query.QueryFactory;
import nu.marginalia.search.query.model.SearchQuery;
import nu.marginalia.search.query.model.UserSearchParameters;
import nu.marginalia.search.svc.SearchQueryIndexService;
import nu.marginalia.search.svc.SearchUnitConversionService;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class SearchOperator {

    private static final Logger logger = LoggerFactory.getLogger(SearchOperator.class);

    // Marker for filtering out sensitive content from the persistent logs
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private final AssistantClient assistantClient;
    private final DbDomainQueries domainQueries;
    private final QueryFactory queryFactory;

    private final SearchQueryIndexService searchQueryService;
    private final SearchUnitConversionService searchUnitConversionService;


    @Inject
    public SearchOperator(AssistantClient assistantClient,
                          DbDomainQueries domainQueries,
                          QueryFactory queryFactory,
                          SearchQueryIndexService searchQueryService,
                          SearchUnitConversionService searchUnitConversionService) {

        this.assistantClient = assistantClient;
        this.domainQueries = domainQueries;
        this.queryFactory = queryFactory;

        this.searchQueryService = searchQueryService;
        this.searchUnitConversionService = searchUnitConversionService;
    }

    public List<UrlDetails> doApiSearch(Context ctx,
                                        UserSearchParameters params) {


        SearchQuery processedQuery = queryFactory.createQuery(params);

        logger.info(queryMarker, "Human terms (API): {}", Strings.join(processedQuery.searchTermsHuman, ','));

        return searchQueryService.executeQuery(ctx, processedQuery);
    }

    public DecoratedSearchResults doSearch(Context ctx, UserSearchParameters params) {

        Future<String> eval = searchUnitConversionService.tryEval(ctx, params.humanQuery());
        SearchQuery processedQuery = queryFactory.createQuery(params);

        logger.info(queryMarker, "Human terms: {}", Strings.join(processedQuery.searchTermsHuman, ','));

        List<UrlDetails> queryResults = searchQueryService.executeQuery(ctx, processedQuery);

        logger.info(queryMarker, "Search Result Count: {}", queryResults.size());

        String evalResult = getFutureOrDefault(eval, "");

        return DecoratedSearchResults.builder()
                .params(params)
                .problems(getProblems(ctx, evalResult, queryResults, processedQuery))
                .evalResult(evalResult)
                .results(queryResults)
                .focusDomain(processedQuery.domain)
                .focusDomainId(getDomainId(processedQuery.domain))
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
        int domainId = -1;
        try {
            if (domain != null) {
                return domainQueries.getDomainId(new EdgeDomain(domain)).id();
            }
        }
        catch (NoSuchElementException ex) {

        }
        return domainId;
    }

    private List<String> getProblems(Context ctx, String evalResult, List<UrlDetails> queryResults, SearchQuery processedQuery) {
        final List<String> problems = new ArrayList<>(processedQuery.problems);
        boolean siteSearch = processedQuery.domain != null;

        if (!siteSearch) {
            if (queryResults.size() <= 5 && null == evalResult) {
                spellCheckTerms(ctx, processedQuery).forEach(problems::add);
            }

            if (queryResults.size() <= 5) {
                problems.add("Try rephrasing the query, changing the word order or using synonyms to get different results. <a href=\"https://memex.marginalia.nu/projects/edge/search-tips.gmi\">Tips</a>.");
            }

            Set<String> representativeKeywords = processedQuery.getAllKeywords();
            if (representativeKeywords.size()>1 && (representativeKeywords.contains("definition") || representativeKeywords.contains("define") || representativeKeywords.contains("meaning")))
            {
                problems.add("Tip: Try using a query that looks like <tt>define:word</tt> if you want a dictionary definition");
            }
        }

        return problems;
    }


    private Iterable<String> spellCheckTerms(Context ctx, SearchQuery disjointedQuery) {
        return Observable.fromIterable(disjointedQuery.searchTermsHuman)
                .subscribeOn(Schedulers.io())
                .flatMap(term -> assistantClient.spellCheck(ctx, term)
                        .onErrorReturn(e -> Collections.emptyList())
                        .filter(results -> hasSpellSuggestions(term, results))
                        .map(suggestions -> searchTermToProblemDescription(term, suggestions))
                )
                .blockingIterable();
    }

    private boolean hasSpellSuggestions(String term, List<String> results) {
        if (results.size() > 1) {
            return true;
        }
        else if (results.size() == 1) {
            return !term.equalsIgnoreCase(results.get(0));
        }
        return false;
    }

    private String searchTermToProblemDescription(String term, List<String> suggestions) {
        return "\"" + term + "\" could be spelled " +
                suggestions.stream().map(s -> "\""+s+"\"").collect(Collectors.joining(", "));
    }


}
