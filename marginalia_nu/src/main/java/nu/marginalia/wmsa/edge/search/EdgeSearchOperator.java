package nu.marginalia.wmsa.edge.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.client.AssistantClient;
import nu.marginalia.wmsa.edge.assistant.dict.WikiArticles;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeIdList;
import nu.marginalia.wmsa.edge.model.id.EdgeIdSet;
import nu.marginalia.wmsa.edge.model.search.*;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchSpecification;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;
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


        EdgeSearchQuery processedQuery = queryFactory.createQuery(params);

        logger.info("Human terms (API): {}", Strings.join(processedQuery.searchTermsHuman, ','));

        return performQuery(ctx, processedQuery);
    }

    public DecoratedSearchResults doSearch(Context ctx, EdgeUserSearchParameters params, @Nullable Future<String> eval) {

        Observable<WikiArticles> definitions = getWikiArticle(ctx, params.humanQuery());

        EdgeSearchQuery processedQuery = queryFactory.createQuery(params);

        logger.info("Human terms: {}", Strings.join(processedQuery.searchTermsHuman, ','));

        List<EdgeUrlDetails> queryResults = performQuery(ctx, processedQuery);

        String evalResult = getEvalResult(eval);
        List<BrowseResult> domainResults = getDomainResults(ctx, processedQuery.specs);
        WikiArticles wikiArticles = definitions.onErrorReturn((e) -> new WikiArticles()).blockingFirst();

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

    private List<BrowseResult> getDomainResults(Context ctx, EdgeSearchSpecification specs) {

        List<String> keywords = specs.subqueries.stream()
                .filter(sq -> sq.searchTermsExclude.isEmpty() && sq.searchTermsInclude.size() == 1)
                .map(sq -> sq.searchTermsInclude.get(0))
                .distinct()
                .toList();

        if (keywords.isEmpty())
            return Collections.emptyList();

        List<EdgeDomainSearchSpecification> requests = new ArrayList<>(keywords.size() * specs.buckets.size());

        for (var keyword : keywords) {
            for (var bucket : specs.buckets) {
                requests.add(new EdgeDomainSearchSpecification(bucket, IndexBlock.Link, keyword,
                        1_000_000, 3, 25));
            }
        }

        EdgeIdSet<EdgeUrl> dedup = new EdgeIdSet<>();
        EdgeIdList<EdgeUrl> values = new EdgeIdList<>();

        for (var result : indexClient.queryDomains(ctx, requests)) {
            for (int id : result.getResults().values()) {
                if (dedup.add(id))
                    values.add(id);
            }
        }

        return edgeDataStoreDao.getBrowseResultFromUrlIds(values);
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

    public List<EdgeUrlDetails> performDumbQuery(Context ctx, EdgeSearchProfile profile, IndexBlock block, int limitPerDomain, int limitTotal, String... termsInclude) {
        List<EdgeSearchSubquery> sqs = new ArrayList<>();

        sqs.add(new EdgeSearchSubquery(Arrays.asList(termsInclude), Collections.emptyList(), block));

        EdgeSearchSpecification specs = new EdgeSearchSpecification(profile.buckets, sqs, 100, limitPerDomain, limitTotal, "", false);

        return performQuery(ctx, new EdgeSearchQuery(specs));
    }

    private List<EdgeUrlDetails> performQuery(Context ctx, EdgeSearchQuery processedQuery) {

        final List<EdgeSearchResultItem> results = indexClient.query(ctx, processedQuery.specs);

        final List<EdgeUrlDetails> resultList = new ArrayList<>(results.size());

        for (var details : resultDecorator.getAllUrlDetails(results)) {
            if (details.getUrlQuality() <= -100) {
                continue;
            }

            details = details.withUrlQualityAdjustment(
                    adjustScoreBasedOnQuery(details, processedQuery.specs));

            resultList.add(details);
        }

        resultList.sort(resultListComparator);

        UrlDeduplicator deduplicator = new UrlDeduplicator(processedQuery.specs.limitByDomain);
        List<EdgeUrlDetails> retList = new ArrayList<>(processedQuery.specs.limitTotal);

        for (var item : resultList) {
            if (retList.size() >= processedQuery.specs.limitTotal)
                break;

            if (!deduplicator.shouldRemove(item)) {
                retList.add(item);
            }
        }

        return retList;
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

        if (!encyclopediaClient.isAlive()) {
            return Observable.just(new WikiArticles());
        }

        return encyclopediaClient
                .encyclopediaLookup(ctx,
                        humanQuery.replaceAll("\\s+", "_")
                                .replaceAll("\"", "")
                )
                .subscribeOn(Schedulers.io())
                .onErrorReturn(e -> new WikiArticles())
                ;
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
