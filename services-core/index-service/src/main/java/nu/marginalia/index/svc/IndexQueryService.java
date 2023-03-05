package nu.marginalia.index.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import nu.marginalia.index.client.model.results.EdgeSearchResultItem;
import nu.marginalia.index.client.model.results.EdgeSearchResultSet;
import nu.marginalia.index.client.model.query.EdgeSearchSpecification;
import nu.marginalia.index.client.model.query.EdgeSearchSubquery;
import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.index.SearchIndexSearchTerms;
import nu.marginalia.index.results.IndexMetadataService;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.index.results.IndexResultValuator;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.results.IndexResultDomainDeduplicator;
import nu.marginalia.index.query.IndexQueryParams;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.svc.searchset.SmallSearchSet;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparingDouble;

@Singleton
public class IndexQueryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");


    private static final Counter wmsa_edge_index_query_timeouts = Counter.build().name("wmsa_edge_index_query_timeouts").help("-").register();
    private static final Gauge wmsa_edge_index_query_cost = Gauge.build().name("wmsa_edge_index_query_cost").help("-").register();
    private static final Histogram wmsa_edge_index_query_time = Histogram.build().name("wmsa_edge_index_query_time").linearBuckets(25/1000., 25/1000., 15).help("-").register();

    private final Gson gson = GsonFactory.get();

    private final SearchIndex index;
    private final IndexSearchSetsService searchSetsService;

    private final IndexMetadataService metadataService;
    private final SearchTermsService searchTermsSvc;


    @Inject
    public IndexQueryService(SearchIndex index,
                             IndexSearchSetsService searchSetsService,
                             IndexMetadataService metadataService,
                             SearchTermsService searchTerms) {
        this.index = index;
        this.searchSetsService = searchSetsService;
        this.metadataService = metadataService;
        this.searchTermsSvc = searchTerms;
    }

    public Object search(Request request, Response response) {
        String json = request.body();
        EdgeSearchSpecification specsSet = gson.fromJson(json, EdgeSearchSpecification.class);

        try {
            return wmsa_edge_index_query_time.time(() -> {
                var params = new SearchParameters(specsSet, getSearchSet(specsSet));

                List<EdgeSearchResultItem> results = executeSearch(params);
                logger.info(queryMarker, "Index Result Count: {}", results.size());

                wmsa_edge_index_query_cost.set(params.getDataCost());
                if (!params.hasTimeLeft()) {
                    wmsa_edge_index_query_timeouts.inc();
                }

                return new EdgeSearchResultSet(results);
            });
        }
        catch (HaltException ex) {
            logger.warn("Halt", ex);
            throw ex;
        }
        catch (Exception ex) {
            logger.info("Error during search {}({}) (query: {})", ex.getClass().getSimpleName(), ex.getMessage(), json);
            logger.info("Error", ex);
            Spark.halt(500, "Error");
            return null;
        }
    }

    // exists for test access
    EdgeSearchResultSet justQuery(EdgeSearchSpecification specsSet) {
        return new EdgeSearchResultSet(executeSearch(new SearchParameters(specsSet, getSearchSet(specsSet))));
    }

    private SearchSet getSearchSet(EdgeSearchSpecification specsSet) {
        if (specsSet.domains != null && !specsSet.domains.isEmpty()) {
            return new SmallSearchSet(specsSet.domains);
        }

        return searchSetsService.getSearchSetByName(specsSet.searchSetIdentifier);
    }

    private List<EdgeSearchResultItem> executeSearch(SearchParameters params) {
        var resultIds = evaluateSubqueries(params);

        var resultItems = calculateResultScores(params, resultIds);

        return selectBestResults(params, resultItems);
    }

    private TLongList evaluateSubqueries(SearchParameters params) {
        final TLongList results = new TLongArrayList(params.fetchSize);

        for (var sq : params.subqueries) {
            final SearchIndexSearchTerms searchTerms = searchTermsSvc.getSearchTerms(sq);

            if (searchTerms.isEmpty()) {
                continue;
            }

            results.addAll(
                    executeSubquery(searchTerms, params)
            );

            if (!params.hasTimeLeft()) {
                logger.info("Query timed out {}, ({}), -{}",
                        sq.searchTermsInclude, sq.searchTermsAdvice, sq.searchTermsExclude);
                break;
            }
        }

        return results;
    }

    private TLongArrayList executeSubquery(SearchIndexSearchTerms terms, SearchParameters params)
    {
        final TLongArrayList results = new TLongArrayList(params.fetchSize);
        final LongQueryBuffer buffer = new LongQueryBuffer(params.fetchSize);

        IndexQuery query = params.createIndexQuery(index, terms);

        while (query.hasMore()
                && results.size() < params.fetchSize
                && params.budget.hasTimeLeft())
        {
            buffer.reset();
            query.getMoreResults(buffer);

            for (int i = 0; i < buffer.size() && results.size() < params.fetchSize; i++) {
                results.add(buffer.data[i]);
            }
        }

        params.dataCost += query.dataCost();

        return results;
    }

    private ArrayList<EdgeSearchResultItem> calculateResultScores(SearchParameters params, TLongList results) {

        final var evaluator = new IndexResultValuator(
                searchTermsSvc,
                metadataService,
                results,
                params.subqueries,
                params.queryParams);

        ArrayList<EdgeSearchResultItem> items = new ArrayList<>(results.size());
        ArrayList<EdgeSearchResultItem> refusedItems = new ArrayList<>(results.size());

        // Sorting the result ids results in better paging characteristics
        results.sort();

        results.forEach(id -> {
            var item = evaluator.evaluateResult(id);

            // Score value is zero when the best params variant consists of low-value terms that are just scattered
            // throughout the document, with no indicators of importance associated with them.
            if (item.getScoreValue() < 0) {
                items.add(item);
            }
            else {
                refusedItems.add(item);
            }

            return true;
        });

        if (items.isEmpty()) {
            items.addAll(refusedItems);
        }

        return items;
    }

    private List<EdgeSearchResultItem> selectBestResults(SearchParameters params, List<EdgeSearchResultItem> results) {

        var domainCountFilter = new IndexResultDomainDeduplicator(params.limitByDomain);

        results.sort(comparingDouble(EdgeSearchResultItem::getScore)
                .thenComparingInt(EdgeSearchResultItem::getRanking)
                .thenComparingInt(EdgeSearchResultItem::getUrlIdInt));

        List<EdgeSearchResultItem> resultsList = new ArrayList<>(results.size());

        for (var item : results) {
            if (domainCountFilter.test(item)) {
                resultsList.add(item);
            }
        }

        if (resultsList.size() > params.limitTotal) {
            // This can't be made a stream limit() operation because we need domainCountFilter
            // to run over the entire list to provide accurate statistics

            resultsList.subList(params.limitTotal, resultsList.size()).clear();
        }

        // populate results with the total number of results encountered from
        // the same domain so this information can be presented to the user
        for (var result : resultsList) {
            result.resultsFromDomain = domainCountFilter.getCount(result);
        }

        return resultsList;
    }

}

class SearchParameters {
    /** This is how many results matching the keywords we'll try to get
      before evaluating them for the best result. */
    final int fetchSize;
    final IndexSearchBudget budget;
    final List<EdgeSearchSubquery> subqueries;
    final IndexQueryParams queryParams;

    final int limitByDomain;
    final int limitTotal;

    // mutable:

    /** An estimate of how much data has been read */
    long dataCost = 0;

    /** A set of id:s considered during each subquery,
     * for deduplication
     */
    final TLongHashSet consideredUrlIds;

    public SearchParameters(EdgeSearchSpecification specsSet, SearchSet searchSet) {
        var limits = specsSet.queryLimits;

        this.fetchSize = limits.fetchSize();
        this.budget = new IndexSearchBudget(limits.timeoutMs());
        this.subqueries = specsSet.subqueries;
        this.limitByDomain = limits.resultsByDomain();
        this.limitTotal = limits.resultsTotal();

        this.consideredUrlIds = new TLongHashSet(fetchSize * 4);

        queryParams = new IndexQueryParams(
                specsSet.quality,
                specsSet.year,
                specsSet.size,
                specsSet.rank,
                searchSet,
                specsSet.queryStrategy);
    }

    IndexQuery createIndexQuery(SearchIndex index, SearchIndexSearchTerms terms) {
        return index.createQuery(terms, queryParams, consideredUrlIds::add);
    }

    boolean hasTimeLeft() {
        return budget.hasTimeLeft();
    }

    long getDataCost() {
        return dataCost;
    }

}

