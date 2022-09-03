package nu.marginalia.wmsa.edge.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Summary;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.client.AssistantClient;
import nu.marginalia.wmsa.edge.assistant.dict.WikiArticles;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.search.*;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchSpecification;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;
import nu.marginalia.wmsa.edge.search.model.DecoratedSearchResultSet;
import nu.marginalia.wmsa.edge.search.model.DecoratedSearchResults;
import nu.marginalia.wmsa.edge.search.query.QueryFactory;
import nu.marginalia.wmsa.edge.search.query.model.EdgeSearchQuery;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import nu.marginalia.wmsa.edge.search.results.SearchResultDecorator;
import nu.marginalia.wmsa.edge.search.results.UrlDeduplicator;
import nu.marginalia.wmsa.encyclopedia.EncyclopediaClient;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class EdgeSearchOperator {

    private static final Logger logger = LoggerFactory.getLogger(EdgeSearchOperator.class);
    private final AssistantClient assistantClient;
    private final EncyclopediaClient encyclopediaClient;
    private final EdgeDataStoreDao edgeDataStoreDao;
    private final EdgeIndexClient indexClient;
    private final QueryFactory queryFactory;
    private final SearchResultDecorator resultDecorator;
    private final Comparator<EdgeUrlDetails> resultListComparator;

    private static final Summary wmsa_search_index_api_time = Summary.build().name("wmsa_search_index_api_time").help("-").register();

    @Inject
    public EdgeSearchOperator(AssistantClient assistantClient,
                              EncyclopediaClient encyclopediaClient,
                              EdgeDataStoreDao edgeDataStoreDao,
                              EdgeIndexClient indexClient,
                              QueryFactory queryFactory,
                              SearchResultDecorator resultDecorator
                              ) {

        this.assistantClient = assistantClient;
        this.encyclopediaClient = encyclopediaClient;
        this.edgeDataStoreDao = edgeDataStoreDao;
        this.indexClient = indexClient;
        this.queryFactory = queryFactory;
        this.resultDecorator = resultDecorator;

        Comparator<EdgeUrlDetails> c = Comparator.comparing(ud -> Math.round(10*(ud.getTermScore() - ud.rankingIdAdjustment())));
        resultListComparator = c.thenComparing(EdgeUrlDetails::getRanking)
                                .thenComparing(EdgeUrlDetails::getId);
    }

    public List<EdgeUrlDetails> doApiSearch(Context ctx,
                                           EdgeUserSearchParameters params) {


        var processedQuery = queryFactory.createQuery(params);

        logger.info("Human terms (API): {}", Strings.join(processedQuery.searchTermsHuman, ','));

        DecoratedSearchResultSet queryResults = performQuery(ctx, processedQuery);

        return queryResults.resultSet;
    }

    public DecoratedSearchResults doSearch(Context ctx, EdgeUserSearchParameters params, @Nullable Future<String> eval) {
        Observable<WikiArticles> definitions = getWikiArticle(ctx, params.humanQuery());
        EdgeSearchQuery processedQuery = queryFactory.createQuery(params);

        logger.info("Human terms: {}", Strings.join(processedQuery.searchTermsHuman, ','));

        DecoratedSearchResultSet queryResults = performQuery(ctx, processedQuery);

        String evalResult = getEvalResult(eval);

        List<BrowseResult> domainResults = getDomainResults(ctx, processedQuery.specs);

        return new DecoratedSearchResults(params,
                getProblems(ctx, params.humanQuery(), evalResult, queryResults, processedQuery),
                evalResult,
                definitions.onErrorReturn((e) -> new WikiArticles()).blockingFirst(),
                queryResults.resultSet,
                domainResults,
                processedQuery.domain,
                getDomainId(processedQuery.domain));
    }

    private List<BrowseResult> getDomainResults(Context ctx, EdgeSearchSpecification specs) {

        List<String> keywords = specs.subqueries.stream()
                .filter(sq -> sq.searchTermsExclude.isEmpty() && sq.searchTermsInclude.size() == 1)
                .map(sq -> sq.searchTermsInclude.get(0))
                .distinct()
                .toList();

        List<EdgeDomainSearchSpecification> requests = new ArrayList<>(keywords.size() * specs.buckets.size());

        for (var keyword : keywords) {
            for (var bucket : specs.buckets) {
                requests.add(new EdgeDomainSearchSpecification(bucket, IndexBlock.Link, keyword,
                        1_000_000, 3, 25));
            }
        }

        if (requests.isEmpty()) {
            return Collections.emptyList();
        }

        Set<EdgeId<EdgeUrl>> results = new LinkedHashSet<>();

        for (var result : indexClient.queryDomains(ctx, requests)) {
            results.addAll(result.getResults());
        }

        return edgeDataStoreDao.getBrowseResultFromUrlIds(new ArrayList<>(results));
    }

    private String getEvalResult(@Nullable Future<String> eval) {
        if (eval == null || eval.isCancelled())  {
            return "";
        }
        try {
            return eval.get(50, TimeUnit.MILLISECONDS);
        }
        catch (Exception ex) {
            logger.warn("Error fetching eval result", ex);
            return "";
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

    public DecoratedSearchResultSet performDumbQuery(Context ctx, EdgeSearchProfile profile, IndexBlock block, int limitPerDomain, int limitTotal, String... termsInclude) {
        List<EdgeSearchSubquery> sqs = new ArrayList<>();

        sqs.add(new EdgeSearchSubquery(Arrays.asList(termsInclude), Collections.emptyList(), block));

        EdgeSearchSpecification specs = new EdgeSearchSpecification(profile.buckets, sqs, 100, limitPerDomain, limitTotal, "", false);

        return performQuery(ctx, new EdgeSearchQuery(specs));
    }

    private DecoratedSearchResultSet performQuery(Context ctx, EdgeSearchQuery processedQuery) {

        List<EdgeUrlDetails> resultList = new ArrayList<>(100);

        for (var s : processedQuery.specs.subqueries) {
            System.out.println(s.block + " : " + s.searchTermsInclude);
        }
        Set<EdgeUrlDetails> queryResults = wmsa_search_index_api_time.time(() -> fetchResultsSimple(ctx, processedQuery));

        for (var details : queryResults) {
            if (details.getUrlQuality() <= -100) {
                continue;
            }

            details = details.withUrlQualityAdjustment(
                    adjustScoreBasedOnQuery(details, processedQuery.specs));

            resultList.add(details);
        }


        resultList.sort(resultListComparator);
        resultList.removeIf(new UrlDeduplicator(processedQuery.specs.limitByDomain)::shouldRemove);

        return new DecoratedSearchResultSet(resultList);
    }

    private List<String> getProblems(Context ctx, String humanQuery, String evalResult, DecoratedSearchResultSet queryResults, EdgeSearchQuery processedQuery) {
        final List<String> problems = new ArrayList<>(processedQuery.problems);
        boolean siteSearch = processedQuery.domain != null;

        if (!siteSearch) {
            if (queryResults.size() <= 5 && null == evalResult) {
                spellCheckTerms(ctx, processedQuery).forEach(problems::add);
            }

            if (queryResults.size() <= 5) {
                problems.add("Try rephrasing the query, changing the word order or using synonyms to get different results. <a href=\"https://memex.marginalia.nu/projects/edge/search-tips.gmi\">Tips</a>.");
            }

            if (humanQuery.toLowerCase().matches(".*(definition|define).*")) {
                problems.add("Tip: Try using a query that looks like <tt>define:word</tt> if you want a dictionary definition");
            }
        }

        if (humanQuery.contains("/")) {
            problems.clear();
            problems.add("<b>There is a known bug with search terms that contain a slash that causes them to be marked as unsupported; as a workaround, try using a dash instead. AC-DC will work, AC/DC does not.</b>");
        }

        return problems;
    }


    private final Pattern titleSplitPattern = Pattern.compile("[:!|./]|(\\s-|-\\s)|\\s{2,}");

    private EdgePageScoreAdjustment adjustScoreBasedOnQuery(EdgeUrlDetails p, EdgeSearchSpecification specs) {
        String titleLC = p.title == null ? "" : p.title.toLowerCase();
        String descLC = p.description == null ? "" : p.description.toLowerCase();
        String urlLC = p.url == null ? "" : p.url.path.toLowerCase();
        String domainLC = p.url == null ? "" : p.url.domain.toString().toLowerCase();

        String[] searchTermsLC = specs.subqueries.get(0).searchTermsInclude.stream()
                .map(String::toLowerCase)
                .flatMap(s -> Arrays.stream(s.split("_")))
                .toArray(String[]::new);
        int termCount = searchTermsLC.length;

        double titleHitsAdj = 0.;
        final String[] titleParts = titleSplitPattern.split(titleLC);
        for (String titlePart : titleParts) {
            double hits = 0;
            for (String term : searchTermsLC) {
                if (titlePart.contains(term)) {
                    hits += term.length();
                }
            }
            titleHitsAdj += hits / Math.max(1, titlePart.length());
        }

        double titleFullHit = 0.;
        if (termCount > 1 && titleLC.contains(specs.humanQuery.replaceAll("\"", "").toLowerCase())) {
            titleFullHit = termCount;
        }
        long descHits = Arrays.stream(searchTermsLC).filter(descLC::contains).count();
        long urlHits = Arrays.stream(searchTermsLC).filter(urlLC::contains).count();
        long domainHits = Arrays.stream(searchTermsLC).filter(domainLC::contains).count();

        double descHitsAdj = 0.;
        for (String word : descLC.split("[^\\w]+")) {
            descHitsAdj += Arrays.stream(searchTermsLC)
                    .filter(term -> term.length() > word.length())
                    .filter(term -> term.contains(word))
                    .mapToDouble(term -> word.length() / (double) term.length())
                    .sum();
        }

        return EdgePageScoreAdjustment.builder()
                .descAdj(Math.min(termCount, descHits) / (10. * termCount))
                .descHitsAdj(descHitsAdj / 10.)
                .domainAdj(2 * Math.min(termCount, domainHits) / (double) termCount)
                .urlAdj(Math.min(termCount, urlHits) / (10. * termCount))
                .titleAdj(5 * titleHitsAdj / (Math.max(1, titleParts.length) * Math.log(titleLC.length() + 2)))
                .titleFullHit(titleFullHit)
                .build();
    }

    @NotNull
    private Observable<WikiArticles> getWikiArticle(Context ctx, String humanQuery) {
        return encyclopediaClient
                .encyclopediaLookup(ctx,
                        humanQuery.replaceAll("\\s+", "_")
                                .replaceAll("\"", "")
                )
                .onErrorReturn(e -> new WikiArticles())
                .subscribeOn(Schedulers.io());
    }

    private Set<EdgeUrlDetails> fetchResultsSimple(Context ctx, EdgeSearchQuery processedQuery) {
        EdgeSearchResultSet resultSet = indexClient.query(ctx, processedQuery.specs);
        Set<EdgeUrlDetails> ret = new HashSet<>();

        logger.debug("{}", resultSet);

        for (IndexBlock block : indexBlockSearchOrder) {
            var results = resultSet.resultsList.getOrDefault(block, Collections.emptyList());

            for (var result : resultDecorator.getAllUrlDetails(results, block)) {
                if (ret.size() > 100) break;
                ret.add(result);
            }
        }

        return ret;
    }

    static final IndexBlock[] indexBlockSearchOrder = Arrays.stream(IndexBlock.values()).sorted(Comparator.comparing(i -> i.sortOrder)).toArray(IndexBlock[]::new);

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
