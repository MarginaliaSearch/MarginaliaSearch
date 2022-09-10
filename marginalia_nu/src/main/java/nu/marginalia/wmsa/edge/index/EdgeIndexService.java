package nu.marginalia.wmsa.edge.index;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import gnu.trove.set.hash.TIntHashSet;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.util.ListChunker;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.model.EdgeIndexSearchTerms;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.IndexQueryCachePool;
import nu.marginalia.wmsa.edge.index.reader.ResultDomainDeduplicator;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexes;
import nu.marginalia.wmsa.edge.index.reader.query.IndexQuery;
import nu.marginalia.wmsa.edge.index.reader.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import nu.marginalia.wmsa.edge.model.id.EdgeIdArray;
import nu.marginalia.wmsa.edge.model.search.*;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchResults;
import nu.marginalia.wmsa.edge.model.search.domain.EdgeDomainSearchSpecification;
import nu.wmsa.wmsa.edge.index.proto.IndexPutKeywordsReq;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;

import static spark.Spark.get;
import static spark.Spark.halt;

public class EdgeIndexService extends Service {
    private static final int SEARCH_BUDGET_TIMEOUT_MS = 3000;
    private static final int QUERY_FETCH_SIZE = 8192;
    private static final int QUERY_FIRST_PASS_DOMAIN_LIMIT = 64;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final Initialization init;
    private final SearchIndexes indexes;
    private final KeywordLexicon keywordLexicon;

    private final Gson gson = GsonFactory.get();

    private static final Histogram wmsa_edge_index_query_time = Histogram.build().name("wmsa_edge_index_query_time").linearBuckets(50, 50, 15).help("-").register();
    private static final Counter wmsa_edge_index_query_timeouts = Counter.build().name("wmsa_edge_index_query_timeouts").help("-").register();

    public static final int DYNAMIC_BUCKET_LENGTH = 7;


    @Inject
    public EdgeIndexService(@Named("service-host") String ip,
                            @Named("service-port") Integer port,
                            Initialization init,
                            MetricsServer metricsServer,
                            SearchIndexes indexes,
                            IndexServicesFactory servicesFactory) {
        super(ip, port, init, metricsServer);

        this.init = init;
        this.indexes = indexes;
        this.keywordLexicon = servicesFactory.getKeywordLexicon();

        Spark.post("/words/", this::putWords);
        Spark.post("/search/", this::search, gson::toJson);
        Spark.post("/search-domain/", this::searchDomain, gson::toJson);

        Spark.post("/dictionary/*", this::getWordId, gson::toJson);

        Spark.post("/ops/repartition", this::repartitionEndpoint);
        Spark.post("/ops/preconvert", this::preconvertEndpoint);
        Spark.post("/ops/reindex/:id", this::reindexEndpoint);

        get("/is-blocked", this::isBlocked, gson::toJson);

        Schedulers.newThread().scheduleDirect(this::initialize, 1, TimeUnit.MICROSECONDS);
    }

    private Object getWordId(Request request, Response response) {
        final String word = request.splat()[0];

        var dr = indexes.getDictionaryReader();
        if (null == dr) {
            response.status(HttpStatus.SC_FAILED_DEPENDENCY);
            return "";
        }

        final int wordId = dr.get(word);

        if (DictionaryHashMap.NO_VALUE == wordId) {
            response.status(404);
            return "";
        }

        return wordId;
    }

    private Object repartitionEndpoint(Request request, Response response) {

        if (!indexes.repartition()) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    private Object preconvertEndpoint(Request request, Response response) {
        if (!indexes.preconvert()) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    private Object reindexEndpoint(Request request, Response response) {
        int id = Integer.parseInt(request.params("id"));

        if (!indexes.reindex(id)) {
            Spark.halt(503, "Operations busy");
        }
        return "OK";
    }

    private Object isBlocked(Request request, Response response) {
        return indexes.isBusy() || !initialized;
    }

    volatile boolean initialized = false;
    public void initialize() {
        if (!initialized) {
            init.waitReady();
            initialized = true;
        }
        else {
            return;
        }
        indexes.initialize(init);
    }

    private Object putWords(Request request, Response response) throws InvalidProtocolBufferException {
        var req = IndexPutKeywordsReq.parseFrom(request.bodyAsBytes());

        EdgeId<EdgeDomain> domainId = new EdgeId<>(req.getDomain());
        EdgeId<EdgeUrl> urlId = new EdgeId<>(req.getUrl());
        int idx = req.getIndex();

        for (int ws = 0; ws < req.getWordSetCount(); ws++) {
            putWords(domainId, urlId, req.getWordSet(ws), idx);
        }

        response.status(HttpStatus.SC_ACCEPTED);
        return "";
    }

    public void putWords(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId,
                         IndexPutKeywordsReq.WordSet words, int idx
    ) {
        SearchIndexJournalWriterImpl indexWriter = indexes.getIndexWriter(idx);

        IndexBlock block = IndexBlock.values()[words.getIndex()];

        for (var chunk : ListChunker.chopList(words.getWordsList(), SearchIndexJournalEntry.MAX_LENGTH)) {

            var entry = new SearchIndexJournalEntry(getOrInsertWordIds(chunk));
            var header = new SearchIndexJournalEntryHeader(domainId, urlId, block);

            indexWriter.put(header, entry);
        };
    }

    private long[] getOrInsertWordIds(List<String> words) {
        long[] ids = new long[words.size()];
        int putIdx = 0;

        for (String word : words) {
            long id = keywordLexicon.getOrInsert(word);
            if (id != DictionaryHashMap.NO_VALUE) {
                ids[putIdx++] = id;
            }
        }

        if (putIdx != words.size()) {
            ids = Arrays.copyOf(ids, putIdx);
        }
        return ids;
    }

    private Object searchDomain(Request request, Response response) {
        if (indexes.getDictionaryReader() == null) {
            logger.warn("Dictionary reader not yet initialized");
            halt(HttpStatus.SC_SERVICE_UNAVAILABLE, "Come back in a few minutes");
        }

        String json = request.body();
        EdgeDomainSearchSpecification specsSet = gson.fromJson(json, EdgeDomainSearchSpecification.class);

        final int wordId = keywordLexicon.getReadOnly(specsSet.keyword);

        EdgeIdArray<EdgeUrl> urlIds = EdgeIdArray.gather(indexes
                .getBucket(specsSet.bucket)
                .findHotDomainsForKeyword(specsSet.block, wordId, specsSet.queryDepth, specsSet.minHitCount, specsSet.maxResults)
                .mapToInt(lv -> (int)(lv & 0xFFFF_FFFFL)));

        return new EdgeDomainSearchResults(specsSet.keyword, urlIds);
    }

    private Object search(Request request, Response response) {
        if (indexes.getDictionaryReader() == null) {
            logger.warn("Dictionary reader not yet initialized");
            halt(HttpStatus.SC_SERVICE_UNAVAILABLE, "Come back in a few minutes");
        }

        String json = request.body();
        EdgeSearchSpecification specsSet = gson.fromJson(json, EdgeSearchSpecification.class);

        long start = System.currentTimeMillis();
        try {
            return new EdgeSearchResultSet(new SearchQuery(specsSet).execute());
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
        finally {
            wmsa_edge_index_query_time.observe(System.currentTimeMillis() - start);
        }
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
                Optional<EdgeIndexSearchTerms> searchTerms = getSearchTerms(sq);

                if (searchTerms.isEmpty())
                    continue;

                results.addAll(performSearch(searchTerms.get(), sq));
            }

            for (var result : results) {
                addResultScores(result);
            }

            if (!budget.hasTimeLeft()) {
                wmsa_edge_index_query_timeouts.inc();
            }

            var domainCountFilter = new ResultDomainDeduplicator(specsSet.limitByDomain);

//            cachePool.printSummary(logger);
            cachePool.clear();

            return results.stream()
                    .sorted(Comparator.comparing(EdgeSearchResultItem::getScore))
                    .filter(domainCountFilter::test)
                    .limit(specsSet.getLimitTotal()).toList();
        }


        private List<EdgeSearchResultItem> performSearch(EdgeIndexSearchTerms searchTerms,
                                                          EdgeSearchSubquery sq)
        {

            final List<EdgeSearchResultItem> results = new ArrayList<>(QUERY_FETCH_SIZE);
            final ResultDomainDeduplicator localFilter = new ResultDomainDeduplicator(QUERY_FIRST_PASS_DOMAIN_LIMIT);

            final int remainingResults = QUERY_FETCH_SIZE;

            for (int indexBucket : specsSet.buckets) {

                if (!budget.hasTimeLeft()) {
                    logger.info("Query timed out, omitting {}:{} for query {}", indexBucket, sq.block, sq.searchTermsInclude);
                    continue;
                }

                if (remainingResults <= results.size())
                    break;

                var query = getQuery(cachePool, indexBucket, sq.block, lv -> localFilter.filterRawValue(indexBucket, lv), searchTerms);
                long[] buf = new long[8192];

                while (query.hasMore() && results.size() < remainingResults && budget.hasTimeLeft()) {
                    int cnt = query.getMoreResults(buf, budget);

                    for (int i = 0; i < cnt && results.size() < remainingResults; i++) {
                        long id = buf[i];

                        final EdgeSearchResultItem ri = new EdgeSearchResultItem(indexBucket, id);

                        if (!seenResults.add(ri.getUrlId().id()) || !localFilter.test(ri)) {
                            continue;
                        }

                        results.add(ri);
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
            final var reader = Objects.requireNonNull(indexes.getDictionaryReader());

            List<List<String>> searchTermVariants = specsSet.subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();

            // Memoize calls to getTermData, as they're redundant and cause disk reads
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


    private Optional<EdgeIndexSearchTerms> getSearchTerms(EdgeSearchSubquery request) {
        final List<Integer> excludes = new ArrayList<>();
        final List<Integer> includes = new ArrayList<>();

        for (var include : request.searchTermsInclude) {
            var word = lookUpWord(include);
            if (word.isEmpty()) {
                logger.debug("Unknown search term: " + include);
                return Optional.empty();
            }
            includes.add(word.getAsInt());
        }

        for (var exclude : request.searchTermsExclude) {
            lookUpWord(exclude).ifPresent(excludes::add);
        }

        if (includes.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new EdgeIndexSearchTerms(includes, excludes));
    }

    private OptionalInt lookUpWord(String s) {
        int ret = indexes.getDictionaryReader().get(s);
        if (ret == DictionaryHashMap.NO_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ret);
    }

}


