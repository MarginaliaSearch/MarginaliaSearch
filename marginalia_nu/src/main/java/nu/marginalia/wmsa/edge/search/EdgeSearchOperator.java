package nu.marginalia.wmsa.edge.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.client.AssistantClient;
import nu.marginalia.wmsa.edge.assistant.dict.WikiArticles;
import nu.marginalia.wmsa.edge.dbcommon.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;
import nu.marginalia.wmsa.edge.search.model.DecoratedSearchResults;
import nu.marginalia.wmsa.edge.search.query.QueryFactory;
import nu.marginalia.wmsa.edge.search.query.model.EdgeSearchQuery;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import nu.marginalia.wmsa.edge.search.svc.EdgeSearchDomainSearchService;
import nu.marginalia.wmsa.edge.search.svc.EdgeSearchQueryIndexService;
import nu.marginalia.wmsa.edge.search.svc.EdgeSearchUnitConversionService;
import nu.marginalia.wmsa.edge.search.svc.EdgeSearchWikiArticlesService;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class EdgeSearchOperator {

    private static final Logger logger = LoggerFactory.getLogger(EdgeSearchOperator.class);
    private final AssistantClient assistantClient;
    private final EdgeDataStoreDao edgeDataStoreDao;
    private final QueryFactory queryFactory;

    private final EdgeSearchQueryIndexService searchQueryService;
    private final EdgeSearchDomainSearchService domainSearchService;
    private final EdgeSearchWikiArticlesService wikiArticlesService;
    private final EdgeSearchUnitConversionService edgeSearchUnitConversionService;


    @Inject
    public EdgeSearchOperator(AssistantClient assistantClient,
                              EdgeDataStoreDao edgeDataStoreDao,
                              QueryFactory queryFactory,

                              EdgeSearchQueryIndexService searchQueryService,
                              EdgeSearchDomainSearchService domainSearchService,
                              EdgeSearchWikiArticlesService wikiArticlesService,
                              EdgeSearchUnitConversionService edgeSearchUnitConversionService) {

        this.assistantClient = assistantClient;
        this.edgeDataStoreDao = edgeDataStoreDao;
        this.queryFactory = queryFactory;

        this.searchQueryService = searchQueryService;
        this.domainSearchService = domainSearchService;
        this.wikiArticlesService = wikiArticlesService;
        this.edgeSearchUnitConversionService = edgeSearchUnitConversionService;
    }

    public List<EdgeUrlDetails> doApiSearch(Context ctx,
                                           EdgeUserSearchParameters params) {


        EdgeSearchQuery processedQuery = queryFactory.createQuery(params);

        logger.info("Human terms (API): {}", Strings.join(processedQuery.searchTermsHuman, ','));

        return searchQueryService.performQuery(ctx, processedQuery);
    }

    public DecoratedSearchResults doSearch(Context ctx, EdgeUserSearchParameters params) {

        Future<WikiArticles> definitions = wikiArticlesService.getWikiArticle(ctx, params.humanQuery());
        Future<String> eval = edgeSearchUnitConversionService.tryEval(ctx, params.humanQuery());
        EdgeSearchQuery processedQuery = queryFactory.createQuery(params);

        logger.info("Human terms: {}", Strings.join(processedQuery.searchTermsHuman, ','));

        List<EdgeUrlDetails> queryResults = searchQueryService.performQuery(ctx, processedQuery);
        List<BrowseResult> domainResults = domainSearchService.getDomainResults(ctx, processedQuery.specs);

        String evalResult = getFutureOrDefault(eval, "");
        WikiArticles wikiArticles = getFutureOrDefault(definitions, new WikiArticles());

        return DecoratedSearchResults.builder()
                .params(params)
                .problems(getProblems(ctx, evalResult, queryResults, processedQuery))
                .evalResult(evalResult)
                .wiki(wikiArticles)
                .results(queryResults)
                .domainResults(domainResults)
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
                return edgeDataStoreDao.getDomainId(new EdgeDomain(domain)).id();
            }
        }
        catch (NoSuchElementException ex) {

        }
        return domainId;
    }

    private List<String> getProblems(Context ctx, String evalResult, List<EdgeUrlDetails> queryResults, EdgeSearchQuery processedQuery) {
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


    private Iterable<String> spellCheckTerms(Context ctx, EdgeSearchQuery disjointedQuery) {
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
