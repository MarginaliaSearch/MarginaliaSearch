package nu.marginalia.wmsa.edge.index.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.dict.OffHeapDictionaryHashMap;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.edge.index.postings.EdgeIndexQuerySearchTerms;
import nu.marginalia.wmsa.edge.index.postings.IndexResultValuator;
import nu.marginalia.wmsa.edge.index.postings.SearchIndexControl;
import nu.marginalia.wmsa.edge.index.query.IndexQuery;
import nu.marginalia.wmsa.edge.index.query.IndexQueryParams;
import nu.marginalia.wmsa.edge.index.query.IndexResultDomainDeduplicator;
import nu.marginalia.wmsa.edge.index.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSet;
import nu.marginalia.wmsa.edge.index.svc.searchset.SmallSearchSet;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultItem;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultSet;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.LongPredicate;

import static java.util.Comparator.comparingDouble;
import static spark.Spark.halt;

@Singleton
public class EdgeIndexQueryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Counter wmsa_edge_index_query_timeouts = Counter.build().name("wmsa_edge_index_query_timeouts").help("-").register();
    private static final Gauge wmsa_edge_index_query_cost = Gauge.build().name("wmsa_edge_index_query_cost").help("-").register();
    private static final Histogram wmsa_edge_index_query_time = Histogram.build().name("wmsa_edge_index_query_time").linearBuckets(25/1000., 25/1000., 15).help("-").register();

    private final Gson gson = GsonFactory.get();

    private final SearchIndexControl indexes;
    private final EdgeIndexSearchSetsService searchSetsService;

    @Inject
    public EdgeIndexQueryService(SearchIndexControl indexes, EdgeIndexSearchSetsService searchSetsService) {
        this.indexes = indexes;
        this.searchSetsService = searchSetsService;
    }

    public Object search(Request request, Response response) {
        if (indexes.getLexiconReader() == null) {
            logger.warn("Dictionary reader not yet initialized");
            halt(HttpStatus.SC_SERVICE_UNAVAILABLE, "Come back in a few minutes");
        }

        String json = request.body();
        EdgeSearchSpecification specsSet = gson.fromJson(json, EdgeSearchSpecification.class);

        try {
            return wmsa_edge_index_query_time.time(() -> query(specsSet));
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


    public EdgeSearchResultSet query(EdgeSearchSpecification specsSet) {
        SearchQuery searchQuery = new SearchQuery(specsSet);

        List<EdgeSearchResultItem> results = searchQuery.execute();

        wmsa_edge_index_query_cost.set(searchQuery.getDataCost());

        if (!searchQuery.hasTimeLeft()) {
            wmsa_edge_index_query_timeouts.inc();
        }

        return new EdgeSearchResultSet(results);
    }

    private class SearchQuery {
        private final int fetchSize;
        private final IndexSearchBudget budget;
        private final List<EdgeSearchSubquery> subqueries;
        private long dataCost = 0;
        private final IndexQueryParams queryParams;

        private final int limitByDomain;
        private final int limitTotal;

        TLongHashSet consideredUrlIds;

        public SearchQuery(EdgeSearchSpecification specsSet) {
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
                    getSearchSet(specsSet),
                    specsSet.queryStrategy);
        }

        private List<EdgeSearchResultItem> execute() {
            final TLongList results = new TLongArrayList(fetchSize);

            for (var sq : subqueries) {
                final EdgeIndexQuerySearchTerms searchTerms = getSearchTerms(sq);

                if (searchTerms.isEmpty()) {
                    continue;
                }

                TLongArrayList resultsForSubquery = performSearch(searchTerms);
                results.addAll(resultsForSubquery);

                if (!budget.hasTimeLeft()) {
                    logger.info("Query timed out {}, ({}), -{}",
                            sq.searchTermsInclude, sq.searchTermsAdvice, sq.searchTermsExclude);
                    break;
                }
            }

            final var evaluator = new IndexResultValuator(indexes, results, subqueries, queryParams);

            ArrayList<EdgeSearchResultItem> items = new ArrayList<>(results.size());
            ArrayList<EdgeSearchResultItem> refusedItems = new ArrayList<>(results.size());

            // Sorting the result ids results in better paging characteristics
            results.sort();

            results.forEach(id -> {
                var item = evaluator.evaluateResult(id);

                // Score value is zero when the best query variant consists of low-value terms that are just scattered
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

            return selectResults(items);
        }


        private TLongArrayList performSearch(EdgeIndexQuerySearchTerms terms)
        {
            final TLongArrayList results = new TLongArrayList(fetchSize);
            final LongQueryBuffer buffer = new LongQueryBuffer(fetchSize);


            IndexQuery query = getQuery(terms, queryParams, consideredUrlIds::add);

            while (query.hasMore() && results.size() < fetchSize && budget.hasTimeLeft()) {
                buffer.reset();
                query.getMoreResults(buffer);

                for (int i = 0; i < buffer.size() && results.size() < fetchSize; i++) {
                    results.add(buffer.data[i]);
                }
            }

            dataCost += query.dataCost();

            return results;
        }

        private SearchSet getSearchSet(EdgeSearchSpecification specsSet) {

            if (specsSet.domains != null && !specsSet.domains.isEmpty()) {
                return new SmallSearchSet(specsSet.domains);
            }

            return searchSetsService.getSearchSetByName(specsSet.searchSetIdentifier);
        }

        private List<EdgeSearchResultItem> selectResults(List<EdgeSearchResultItem> results) {

            var domainCountFilter = new IndexResultDomainDeduplicator(limitByDomain);

            results.sort(comparingDouble(EdgeSearchResultItem::getScore)
                    .thenComparingInt(EdgeSearchResultItem::getRanking)
                    .thenComparingInt(EdgeSearchResultItem::getUrlIdInt));

            List<EdgeSearchResultItem> resultsList = new ArrayList<>(results.size());

            for (var item : results) {
                if (domainCountFilter.test(item)) {
                    resultsList.add(item);
                }
            }

            if (resultsList.size() > limitTotal) {
                // This can't be made a stream limit() operation because we need domainCountFilter
                // to run over the entire list to provide accurate statistics

                resultsList.subList(limitTotal, resultsList.size()).clear();
            }

            for (var result : resultsList) {
                result.resultsFromDomain = domainCountFilter.getCount(result);
            }

            return resultsList;
        }

        private IndexQuery getQuery(EdgeIndexQuerySearchTerms terms, IndexQueryParams params, LongPredicate includePred) {
            return indexes.getIndex().getQuery(terms, params, includePred);
        }

        public boolean hasTimeLeft() {
            return budget.hasTimeLeft();
        }

        public long getDataCost() {
            return dataCost;
        }

    }

    private EdgeIndexQuerySearchTerms getSearchTerms(EdgeSearchSubquery request) {
        final IntList excludes = new IntArrayList();
        final IntList includes = new IntArrayList();
        final IntList priority = new IntArrayList();

        for (var include : request.searchTermsInclude) {
            var word = lookUpWord(include);
            if (word.isEmpty()) {
                logger.debug("Unknown search term: " + include);
                return new EdgeIndexQuerySearchTerms();
            }
            includes.add(word.getAsInt());
        }

        for (var advice : request.searchTermsAdvice) {
            var word = lookUpWord(advice);
            if (word.isEmpty()) {
                logger.debug("Unknown search term: " + advice);
                return new EdgeIndexQuerySearchTerms();
            }
            includes.add(word.getAsInt());
        }

        for (var exclude : request.searchTermsExclude) {
            lookUpWord(exclude).ifPresent(excludes::add);
        }
        for (var exclude : request.searchTermsPriority) {
            lookUpWord(exclude).ifPresent(priority::add);
        }

        return new EdgeIndexQuerySearchTerms(includes, excludes, priority);
    }


    private OptionalInt lookUpWord(String s) {
        int ret = indexes.getLexiconReader().get(s);
        if (ret == OffHeapDictionaryHashMap.NO_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ret);
    }

}
