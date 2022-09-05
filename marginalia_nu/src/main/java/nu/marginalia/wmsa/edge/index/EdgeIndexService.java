package nu.marginalia.wmsa.edge.index;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import io.prometheus.client.Histogram;
import io.reactivex.rxjava3.schedulers.Schedulers;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import nu.marginalia.util.ListChunker;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntryHeader;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.model.EdgeIndexSearchTerms;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexes;
import nu.marginalia.wmsa.edge.index.reader.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
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
import java.util.stream.LongStream;

import static java.lang.Math.min;
import static spark.Spark.get;
import static spark.Spark.halt;

public class EdgeIndexService extends Service {
    private static final int SEARCH_BUDGET_TIMEOUT_MS = 3000;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final Initialization init;
    private final SearchIndexes indexes;
    private final KeywordLexicon keywordLexicon;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapterFactory(RecordTypeAdapterFactory.builder().allowMissingComponentValues().create())
            .create();

    private static final Histogram wmsa_edge_index_query_time
            = Histogram.build().name("wmsa_edge_index_query_time")
                               .linearBuckets(50, 50, 15)
                               .help("-").register();

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
        int putId = 0;

        for (String word : words) {
            long id = keywordLexicon.getOrInsert(word);
            if (id != DictionaryHashMap.NO_VALUE) {
                ids[putId++] = id;
            }
        }

        if (putId != words.size()) {
            ids = Arrays.copyOf(ids, putId);
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

        List<EdgeId<EdgeUrl>> urlIds = indexes
                .getBucket(specsSet.bucket)
                .findHotDomainsForKeyword(specsSet.block, wordId, specsSet.queryDepth, specsSet.minHitCount, specsSet.maxResults)
                .mapToObj(lv -> new EdgeId<EdgeUrl>((int)(lv & 0xFFFF_FFFFL)))
                .toList();

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
        private final TIntHashSet seenResults = new TIntHashSet();
        private final ResultDomainDeduplicator domainCountFilter;
        private final EdgeSearchSpecification specsSet;
        private final IndexSearchBudget budget = new IndexSearchBudget(SEARCH_BUDGET_TIMEOUT_MS);

        public SearchQuery(EdgeSearchSpecification specsSet) {
            domainCountFilter = new ResultDomainDeduplicator(specsSet.limitByDomain);

            this.specsSet = specsSet;
        }

        private Map<IndexBlock, List<EdgeSearchResultItem>> execute() {
            final Map<IndexBlock, List<EdgeSearchResultItem>> results = new HashMap<>();
            int totalResultsCount = 0;

            for (var sq : specsSet.subqueries) {
                Optional<EdgeIndexSearchTerms> searchTerms = getSearchTerms(sq);

                if (searchTerms.isEmpty())
                    continue;

                var resultForSq = performSearch(searchTerms.get(), sq, totalResultsCount);

                totalResultsCount += resultForSq.size();
                if (!resultForSq.isEmpty()) {
                    results.computeIfAbsent(sq.block, s -> new ArrayList<>()).addAll(resultForSq);
                }
            }

            addResultScores(results);

            return results;
        }


        private List<EdgeSearchResultItem> performSearch(EdgeIndexSearchTerms searchTerms,
                                                          EdgeSearchSubquery sq,
                                                          int totalResultsCount)
        {
            if (specsSet.limitTotal <= totalResultsCount)
                return new ArrayList<>();

            final List<EdgeSearchResultItem> results = new ArrayList<>();
            final ResultDomainDeduplicator localFilter = new ResultDomainDeduplicator(specsSet.limitByDomain);

            final int remainingResults = min(
                    specsSet.limitByBucket,
                    specsSet.limitTotal - totalResultsCount
            );

            for (int indexBucket : specsSet.buckets) {

                if (!budget.hasTimeLeft()) {
                    System.out.println("Timed out, omitting " + indexBucket + ":" + sq);
                }

                if (remainingResults <= results.size())
                    break;

                PrimitiveIterator.OfLong queryIter =
                        getQuery(indexBucket, sq.block, lv -> localFilter.filterRawValue(indexBucket, lv), searchTerms)
                        .iterator();

                while (queryIter.hasNext() && remainingResults > results.size()) {
                    final long id = queryIter.nextLong();
                    final EdgeSearchResultItem ri = new EdgeSearchResultItem(indexBucket, sq.termSize(), id);

                    if (!seenResults.add(ri.url.id()) || !localFilter.test(domainCountFilter, ri)) {
                        continue;
                    }

                    domainCountFilter.add(ri);
                    results.add(ri);
                }

            }

            return results;
        }

        private LongStream getQuery(int bucket, IndexBlock block,
                                    LongPredicate filter, EdgeIndexSearchTerms searchTerms) {

            if (!indexes.isValidBucket(bucket)) {
                logger.warn("Invalid bucket {}", bucket);
                return LongStream.empty();
            }

            return indexes.getBucket(bucket).getQuery(block, filter, budget, searchTerms);
        }

        private void addResultScores(Map<IndexBlock, List<EdgeSearchResultItem>> results) {
            List<List<String>> distinctSearchTerms = specsSet.subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();

            // This looks far worse than it is, most loops are only a few iterations long

            for (var blockResults : results.values()) { // typical length 1-2
                for (var result : blockResults) { // typical length 20-100
                    for (int i = 0; i < distinctSearchTerms.size(); i++) { // typical length 1-2
                        for (var term : distinctSearchTerms.get(i)) {  // typical length 1-6

                            final int termId = indexes.getDictionaryReader().get(term);
                            final EdgeIndexBucket bucket = indexes.getBucket(result.bucketId);
                            final long combinedUrlId = result.getCombinedId();

                            result.scores.add(new EdgeSearchResultKeywordScore(i, term,
                                    bucket.getTermScore(termId, combinedUrlId),
                                    bucket.isTermInBucket(IndexBlock.Title, termId, combinedUrlId),
                                    bucket.isTermInBucket(IndexBlock.Link, termId, combinedUrlId),
                                    bucket.isTermInBucket(IndexBlock.Site, termId, combinedUrlId),
                                    bucket.isTermInBucket(IndexBlock.Subjects, termId, combinedUrlId),
                                    bucket.isTermInBucket(IndexBlock.NamesWords, termId, combinedUrlId)
                            ));
                        }
                    }
                }
            }
        }
    }

    private static class ResultDomainDeduplicator {
        final TLongIntMap resultsByDomain = new TLongIntHashMap(200, 0.75f, -1, 0);
        final int limitByDomain;

        ResultDomainDeduplicator(int limitByDomain) {
            this.limitByDomain = limitByDomain;
        }

        public boolean filterRawValue(int bucket, long value) {
            int domain = (int)(value >>> 32);

            if (domain == Integer.MAX_VALUE) {
                return true;
            }

            return resultsByDomain.get(getKey(bucket, domain)) <= limitByDomain;
        }

        long getKey(int bucketId, int domainId) {
            return ((long)bucketId) << 32 | domainId;
        }
        long getKey(EdgeSearchResultItem item) {
            return ((long)item.bucketId) << 32 | item.domain.id();
        }

        public boolean test(EdgeSearchResultItem item) {
            if (item.domain.id() == Integer.MAX_VALUE) {
                return true;
            }

            return resultsByDomain.adjustOrPutValue(getKey(item), 1, 1) <= limitByDomain;
        }

        int getCount(EdgeSearchResultItem item) {
            return resultsByDomain.get(getKey(item));
        }

        public void addAll(List<EdgeSearchResultItem> items) {
            items.forEach(item -> {
                resultsByDomain.adjustOrPutValue(getKey(item), 1, 1);
            });
        }
        public void add(EdgeSearchResultItem item) {
            resultsByDomain.adjustOrPutValue(getKey(item), 1, 1);
        }
        public boolean test(ResultDomainDeduplicator root, EdgeSearchResultItem item) {
            if (item.domain.id() == Integer.MAX_VALUE) {
                return true;
            }
            return root.getCount(item) + resultsByDomain.adjustOrPutValue(getKey(item), 1, 1) <= limitByDomain;
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

