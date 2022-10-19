package nu.marginalia.wmsa.edge.index.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.set.hash.TIntHashSet;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import nu.marginalia.util.btree.BTreeQueryBuffer;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.edge.index.EdgeIndexBucket;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordMetadata;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexes;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQuery;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQueryParams;
import nu.marginalia.wmsa.edge.index.svc.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.index.svc.query.ResultDomainDeduplicator;
import nu.marginalia.wmsa.edge.model.search.*;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.*;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static spark.Spark.halt;

@Singleton
public class EdgeIndexQueryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int QUERY_FIRST_PASS_DOMAIN_LIMIT = 64;

    private static final Counter wmsa_edge_index_query_timeouts = Counter.build().name("wmsa_edge_index_query_timeouts").help("-").register();

    private static final Gauge wmsa_edge_index_query_cost = Gauge.build().name("wmsa_edge_index_query_cost").help("-").register();
    private static final Histogram wmsa_edge_index_query_time = Histogram.build().name("wmsa_edge_index_query_time").linearBuckets(25/1000., 25/1000., 15).help("-").register();

    private final Gson gson = GsonFactory.get();

    private final SearchIndexes indexes;

    @Inject
    public EdgeIndexQueryService(SearchIndexes indexes) {
        this.indexes = indexes;
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
        private final TIntHashSet seenResults;
        private final EdgeSearchSpecification specsSet;
        private final IndexSearchBudget budget;
        private final Integer qualityLimit;
        private final Integer rankLimit;
        private long dataCost = 0;

        public SearchQuery(EdgeSearchSpecification specsSet) {
            this.specsSet = specsSet;
            this.budget = new IndexSearchBudget(specsSet.timeoutMs);
            this.fetchSize = specsSet.fetchSize;
            this.seenResults =  new TIntHashSet(fetchSize, 0.5f);
            this.qualityLimit = specsSet.quality;
            this.rankLimit = specsSet.rank;
        }

        private List<EdgeSearchResultItem> execute() {
            final Set<EdgeSearchResultItem> results = new HashSet<>(fetchSize);

            for (var sq : specsSet.subqueries) {
                results.addAll(performSearch(sq));
            }

            final SearchTermEvaluator evaluator = new SearchTermEvaluator(specsSet, results);
            for (var result : results) {
                evaluator.addResultScores(result);
            }

            return createResultList(results);
        }

        private List<EdgeSearchResultItem> createResultList(Set<EdgeSearchResultItem> results) {

            var domainCountFilter = new ResultDomainDeduplicator(specsSet.limitByDomain);

            List<EdgeSearchResultItem> resultList = results.stream()
                    .sorted(
                            comparing(EdgeSearchResultItem::getScore)
                                    .thenComparing(EdgeSearchResultItem::getRanking)
                                    .thenComparing(EdgeSearchResultItem::getUrlIdInt)
                    )
                    .filter(domainCountFilter::test)
                    .collect(Collectors.toList());

            if (resultList.size() > specsSet.getLimitTotal()) {
                // This can't be made a stream limit() operation because we need domainCountFilter
                // to run over the entire list to provide accurate statistics

                resultList.subList(specsSet.getLimitTotal(), resultList.size()).clear();
            }

            for (var result : resultList) {
                result.resultsFromDomain = domainCountFilter.getCount(result);
            }

            return resultList;
        }


        private List<EdgeSearchResultItem> performSearch(EdgeSearchSubquery sq)
        {

            final List<EdgeSearchResultItem> results = new ArrayList<>(fetchSize);
            final SearchTerms searchTerms = getSearchTerms(sq);

            if (searchTerms.isEmpty()) {
                return Collections.emptyList();
            }

            final BTreeQueryBuffer buffer = new BTreeQueryBuffer(fetchSize);

            for (int indexBucket : specsSet.buckets) {
                final ResultDomainDeduplicator localFilter = new ResultDomainDeduplicator(QUERY_FIRST_PASS_DOMAIN_LIMIT);

                if (!budget.hasTimeLeft()) {
                    logger.info("Query timed out, omitting {}:{} for query {}, ({}), -{}",
                            indexBucket, sq.block, sq.searchTermsInclude, sq.searchTermsAdvice, sq.searchTermsExclude);
                    continue;

                }

                if (results.size() >= fetchSize) {
                    break;
                }

                IndexQueryParams queryParams = new IndexQueryParams(sq.block, searchTerms, qualityLimit, rankLimit, specsSet.domains);

                IndexQuery query = getQuery(indexBucket, localFilter::filterRawValue, queryParams);

                while (query.hasMore() && results.size() < fetchSize && budget.hasTimeLeft()) {
                    buffer.reset();
                    query.getMoreResults(buffer);

                    for (int i = 0; i < buffer.size() && results.size() < fetchSize; i++) {
                        final long id = buffer.data[i];

                        if (!seenResults.add((int)(id & 0xFFFF_FFFFL)) || !localFilter.test(id)) {
                            continue;
                        }

                        results.add(new EdgeSearchResultItem(indexBucket, sq.block, id));
                    }
                }

                dataCost += query.dataCost();

            }

            return results;
        }

        private IndexQuery getQuery(int bucket, LongPredicate filter, IndexQueryParams params) {

            if (!indexes.isValidBucket(bucket)) {
                logger.warn("Invalid bucket {}", bucket);
                return new IndexQuery(Collections.emptyList());
            }

            return indexes.getBucket(bucket).getQuery(filter, params);
        }

        public boolean hasTimeLeft() {
            return budget.hasTimeLeft();
        }

        private record IndexAndBucket(IndexBlock block, int bucket) {}

        public long getDataCost() {
            return dataCost;
        }

        record ResultTerm (int bucket, int termId, long combinedUrlId) {}
    }

    public class SearchTermEvaluator {
        private static final EdgePageWordMetadata blankMetadata = new EdgePageWordMetadata(EdgePageWordMetadata.emptyValue());

        private final Map<SearchQuery.ResultTerm, EdgePageWordMetadata> termData = new HashMap<>(16);

        private final List<List<String>> searchTermVariants;

        public SearchTermEvaluator(EdgeSearchSpecification specsSet, Set<EdgeSearchResultItem> results) {
            this.searchTermVariants = specsSet.subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();

            final int[] termIdsAll = getIncludeTermIds(specsSet);

            Map<SearchQuery.IndexAndBucket, LongAVLTreeSet> resultIdsByBucket = new HashMap<>(7);

            for (int termId : termIdsAll) {

                for (var result: results) {
                    resultIdsByBucket
                            .computeIfAbsent(new SearchQuery.IndexAndBucket(result.block, result.bucketId),
                                    id -> new LongAVLTreeSet())
                            .add(result.combinedId);
                }

                resultIdsByBucket.forEach((indexAndBucket, resultIds) ->
                        loadMetadata(termId, indexAndBucket.bucket, indexAndBucket.block, resultIds));

                resultIdsByBucket.clear();
            }
        }

        private int[] getIncludeTermIds(EdgeSearchSpecification specsSet) {

            final var reader = Objects.requireNonNull(indexes.getLexiconReader());

            final List<String> terms = specsSet.allIncludeSearchTerms();
            final IntList ret = new IntArrayList(terms.size());

            for (var term : terms) {
                int id = reader.get(term);

                if (id >= 0)
                    ret.add(id);
            }

            return ret.toIntArray();
        }

        private void loadMetadata(int termId, int bucket, IndexBlock indexBlock,
                                  LongAVLTreeSet docIdsMissingMetadata)
        {
            EdgeIndexBucket index = indexes.getBucket(bucket);

            if (docIdsMissingMetadata.isEmpty())
                return;


            long[] ids = docIdsMissingMetadata.toLongArray();
            long[] metadata = index.getMetadata(indexBlock, termId, ids);

            for (int i = 0; i < metadata.length; i++) {
                if (metadata[i] == 0L)
                    continue;

                termData.put(
                        new SearchQuery.ResultTerm(bucket, termId, ids[i]),
                        new EdgePageWordMetadata(metadata[i])
                );

                docIdsMissingMetadata.remove(ids[i]);
            }
        }

        public void addResultScores(EdgeSearchResultItem searchResult) {
            final var reader = Objects.requireNonNull(indexes.getLexiconReader());

            double bestScore = 0;

            for (int searchTermListIdx = 0; searchTermListIdx < searchTermVariants.size(); searchTermListIdx++) {
                double setScore = 0;
                int setSize = 0;
                var termList = searchTermVariants.get(searchTermListIdx);

                for (int termIdx = 0; termIdx < termList.size(); termIdx++) {
                    String searchTerm = termList.get(termIdx);

                    final int termId = reader.get(searchTerm);

                    var key = new SearchQuery.ResultTerm(searchResult.bucketId, termId, searchResult.getCombinedId());
                    var metadata = termData.getOrDefault(key, blankMetadata);

                    EdgeSearchResultKeywordScore score = new EdgeSearchResultKeywordScore(searchTermListIdx, searchTerm, metadata);

                    searchResult.scores.add(score);
                    setScore += score.termValue();
                    if (termIdx == 0) {
                        setScore += score.documentValue();
                    }

                    setSize++;
                }
                bestScore = Math.min(bestScore, setScore/setSize);
            }

            searchResult.setScore(bestScore);
        }


    }

    private SearchTerms getSearchTerms(EdgeSearchSubquery request) {
        final IntList excludes = new IntArrayList();
        final IntList includes = new IntArrayList();

        for (var include : request.searchTermsInclude) {
            var word = lookUpWord(include);
            if (word.isEmpty()) {
                logger.debug("Unknown search term: " + include);
                return new SearchTerms();
            }
            includes.add(word.getAsInt());
        }

        for (var advice : request.searchTermsAdvice) {
            var word = lookUpWord(advice);
            if (word.isEmpty()) {
                logger.debug("Unknown search term: " + advice);
                return new SearchTerms();
            }
            includes.add(word.getAsInt());
        }

        for (var exclude : request.searchTermsExclude) {
            lookUpWord(exclude).ifPresent(excludes::add);
        }

        return new SearchTerms(includes, excludes);
    }

    public record SearchTerms(IntList includes, IntList excludes) {
        public SearchTerms() {
            this(IntList.of(), IntList.of());
        }

        public boolean isEmpty() {
            return includes.isEmpty();
        }

        public int[] sortedDistinctIncludes(IntComparator comparator) {
            if (includes.isEmpty())
                return includes.toIntArray();

            IntList list = new IntArrayList(new IntOpenHashSet(includes));
            list.sort(comparator);
            return list.toIntArray();
        }
    }


    private OptionalInt lookUpWord(String s) {
        int ret = indexes.getLexiconReader().get(s);
        if (ret == DictionaryHashMap.NO_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ret);
    }

}
