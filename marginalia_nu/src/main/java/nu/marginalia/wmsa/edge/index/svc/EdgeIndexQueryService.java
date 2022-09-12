package nu.marginalia.wmsa.edge.index.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.set.hash.TIntHashSet;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.index.EdgeIndexBucket;
import nu.marginalia.wmsa.edge.index.model.EdgeIndexSearchTerms;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexes;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQuery;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQueryCachePool;
import nu.marginalia.wmsa.edge.index.svc.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.index.svc.query.ResultDomainDeduplicator;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeIdList;
import nu.marginalia.wmsa.edge.model.search.*;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchResults;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchSpecification;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.*;
import java.util.function.LongPredicate;

import static java.util.Comparator.comparing;
import static spark.Spark.halt;

@Singleton
public class EdgeIndexQueryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int SEARCH_BUDGET_TIMEOUT_MS = 250;
    private static final int QUERY_FETCH_SIZE = 8192;
    private static final int QUERY_FIRST_PASS_DOMAIN_LIMIT = 64;

    private static final Counter wmsa_edge_index_query_timeouts = Counter.build().name("wmsa_edge_index_query_timeouts").help("-").register();

    private static final Histogram wmsa_edge_index_query_time = Histogram.build().name("wmsa_edge_index_query_time").linearBuckets(50, 50, 15).help("-").register();
    private static final Histogram wmsa_edge_index_domain_query_time = Histogram.build().name("wmsa_edge_index_domain_query_time").linearBuckets(50, 50, 15).help("-").register();

    private final Gson gson = GsonFactory.get();

    private final SearchIndexes indexes;

    @Inject
    public EdgeIndexQueryService(SearchIndexes indexes) {
        this.indexes = indexes;
    }

    public Object searchDomain(Request request, Response response) {
        if (indexes.getLexiconReader() == null) {
            logger.warn("Dictionary reader not yet initialized");
            halt(HttpStatus.SC_SERVICE_UNAVAILABLE, "Come back in a few minutes");
        }

        String json = request.body();
        EdgeDomainSearchSpecification specsSet = gson.fromJson(json, EdgeDomainSearchSpecification.class);

        try {
            return wmsa_edge_index_domain_query_time.time(() -> queryDomain(specsSet));
        }
        catch (HaltException ex) {
            logger.warn("Halt", ex);
            throw ex;
        }
        catch (Exception ex) {
            logger.info("Error during domain search {}({}) (query: {})", ex.getClass().getSimpleName(), ex.getMessage(), json);
            logger.info("Error", ex);
            Spark.halt(500, "Error");
            return null;
        }
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
        List<EdgeSearchResultItem> results = new SearchQuery(specsSet).execute();
        return new EdgeSearchResultSet(results);
    }

    public EdgeDomainSearchResults queryDomain(EdgeDomainSearchSpecification specsSet) {

        final OptionalInt wordId = lookUpWord(specsSet.keyword);
        EdgeIdList<EdgeUrl> urlIds;

        if (wordId.isEmpty()) {
            urlIds = new EdgeIdList<>();
        } else {
            urlIds = indexes
                    .getBucket(specsSet.bucket)
                    .findHotDomainsForKeyword(specsSet.block, wordId.getAsInt(), specsSet.queryDepth, specsSet.minHitCount, specsSet.maxResults)
                    .mapToInt(lv -> (int) (lv & 0xFFFF_FFFFL))
                    .collect(EdgeIdList::new, EdgeIdList::add, EdgeIdList::addAll);
        }

        return new EdgeDomainSearchResults(specsSet.keyword, urlIds);
    }

    private class SearchQuery {
        private final TIntHashSet seenResults = new TIntHashSet(QUERY_FETCH_SIZE, 0.5f);
        private final EdgeSearchSpecification specsSet;
        private final IndexSearchBudget budget = new IndexSearchBudget(SEARCH_BUDGET_TIMEOUT_MS);
        private final IndexQueryCachePool cachePool = new IndexQueryCachePool();

        public SearchQuery(EdgeSearchSpecification specsSet) {
            this.specsSet = specsSet;
        }

        private List<EdgeSearchResultItem> execute() {
            final Set<EdgeSearchResultItem> results = new HashSet<>(QUERY_FETCH_SIZE);

            for (var sq : specsSet.subqueries) {
                results.addAll(performSearch(sq));
            }

            for (var result : results) {
                addResultScores(result);
            }

            if (!budget.hasTimeLeft()) {
                wmsa_edge_index_query_timeouts.inc();
            }

            var domainCountFilter = new ResultDomainDeduplicator(specsSet.limitByDomain);

            if (WmsaHome.isDebug()) {
                cachePool.printSummary(logger);
            }
            cachePool.clear();

            return results.stream()
                    .sorted(
                            comparing(EdgeSearchResultItem::getScore)
                                .thenComparing(EdgeSearchResultItem::getRanking)
                                .thenComparing(EdgeSearchResultItem::getUrlIdInt)
                    )
                    .filter(domainCountFilter::test)
                    .limit(specsSet.getLimitTotal()).toList();
        }


        private List<EdgeSearchResultItem> performSearch(EdgeSearchSubquery sq)
        {

            final List<EdgeSearchResultItem> results = new ArrayList<>(QUERY_FETCH_SIZE);
            final EdgeIndexSearchTerms searchTerms = getSearchTerms(sq);

            if (searchTerms.isEmpty())
                return Collections.emptyList();

            for (int indexBucket : specsSet.buckets) {
                final ResultDomainDeduplicator localFilter = new ResultDomainDeduplicator(QUERY_FIRST_PASS_DOMAIN_LIMIT);

                if (!budget.hasTimeLeft()) {
                    logger.info("Query timed out, omitting {}:{} for query {}", indexBucket, sq.block, sq.searchTermsInclude);
                    continue;
                }

                if (QUERY_FETCH_SIZE <= results.size())
                    break;

                IndexQuery query = getQuery(cachePool, indexBucket, sq.block, localFilter::filterRawValue, searchTerms);
                long[] buf = new long[8192];

                while (query.hasMore() && results.size() < QUERY_FETCH_SIZE && budget.hasTimeLeft()) {
                    int cnt = query.getMoreResults(buf, budget);

                    for (int i = 0; i < cnt && results.size() < QUERY_FETCH_SIZE; i++) {
                        final long id = buf[i];

                        if (!seenResults.add((int)(id & 0xFFFF_FFFFL)) || !localFilter.test(id)) {
                            continue;
                        }

                        results.add(new EdgeSearchResultItem(indexBucket, id));
                    }
                }

            }

            return results;
        }

        private IndexQuery getQuery(IndexQueryCachePool cachePool, int bucket, IndexBlock block,
                                    LongPredicate filter, EdgeIndexSearchTerms searchTerms) {

            if (!indexes.isValidBucket(bucket)) {
                logger.warn("Invalid bucket {}", bucket);
                return new IndexQuery(Collections.emptyList());
            }

            return indexes.getBucket(bucket).getQuery(cachePool, block, filter, searchTerms);
        }

        private void addResultScores(EdgeSearchResultItem searchResult) {
            final var reader = Objects.requireNonNull(indexes.getLexiconReader());

            List<List<String>> searchTermVariants = specsSet.subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();

            // Memoize calls to getTermData, as they're somewhat expensive and highly redundant
            Map<ResultTerm, ResultTermData> termMetadata = new HashMap<>(32);

            double bestScore = 0;

            for (int searchTermListIdx = 0; searchTermListIdx < searchTermVariants.size(); searchTermListIdx++) {
                double setScore = 0;
                int setSize = 0;
                for (var searchTerm : searchTermVariants.get(searchTermListIdx)) {

                    final int termId = reader.get(searchTerm);

                    ResultTermData data = termMetadata.computeIfAbsent(
                            new ResultTerm(searchResult.bucketId, termId, searchResult.getCombinedId()), this::getTermData);

                    var score = data.asScore(searchTermListIdx, searchTerm);
                    searchResult.scores.add(score);
                    setScore += score.value();
                    setSize++;
                }
                bestScore = Math.min(bestScore, setScore/setSize);
            }

            searchResult.setScore(bestScore);
        }

        private ResultTermData getTermData(ResultTerm resultTerm) {
            final EdgeIndexBucket bucket = indexes.getBucket(resultTerm.bucket);
            final int termId = resultTerm.termId;
            final long combinedUrlId = resultTerm.combinedUrlId;

            return new ResultTermData(bucket.getTermScore(cachePool, termId, combinedUrlId),
                    bucket.isTermInBucket(cachePool, IndexBlock.Title, termId, combinedUrlId),
                    bucket.isTermInBucket(cachePool, IndexBlock.Link, termId, combinedUrlId),
                    bucket.isTermInBucket(cachePool, IndexBlock.Site, termId, combinedUrlId),
                    bucket.isTermInBucket(cachePool, IndexBlock.Subjects, termId, combinedUrlId),
                    bucket.isTermInBucket(cachePool, IndexBlock.NamesWords, termId, combinedUrlId),
                    bucket.isTermInBucket(cachePool, IndexBlock.Tfidf_Top, termId, combinedUrlId),
                    bucket.isTermInBucket(cachePool, IndexBlock.Tfidf_Middle, termId, combinedUrlId),
                    bucket.isTermInBucket(cachePool, IndexBlock.Tfidf_Lower, termId, combinedUrlId)
            );
        }

        record ResultTerm (int bucket, int termId, long combinedUrlId) {}
        record ResultTermData (IndexBlock index,
                               boolean title,
                               boolean link,
                               boolean site,
                               boolean subject,
                               boolean name,
                               boolean high,
                               boolean mid,
                               boolean low
        ) {
            public EdgeSearchResultKeywordScore asScore(int set, String searchTerm) {
                return new EdgeSearchResultKeywordScore(set, searchTerm, index, title, link, site, subject, name, high, mid, low);
            }
        }
    }


    private EdgeIndexSearchTerms getSearchTerms(EdgeSearchSubquery request) {
        final List<Integer> excludes = new ArrayList<>();
        final List<Integer> includes = new ArrayList<>();

        for (var include : request.searchTermsInclude) {
            var word = lookUpWord(include);
            if (word.isEmpty()) {
                logger.debug("Unknown search term: " + include);
                return new EdgeIndexSearchTerms(Collections.emptyList(), Collections.emptyList());
            }
            includes.add(word.getAsInt());
        }

        for (var exclude : request.searchTermsExclude) {
            lookUpWord(exclude).ifPresent(excludes::add);
        }

        return new EdgeIndexSearchTerms(includes, excludes);
    }


    private OptionalInt lookUpWord(String s) {
        int ret = indexes.getLexiconReader().get(s);
        if (ret == DictionaryHashMap.NO_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ret);
    }

}
