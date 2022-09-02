package nu.marginalia.wmsa.edge.index.reader.query;

import com.google.common.collect.Streams;
import nu.marginalia.wmsa.edge.index.reader.SearchIndex;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class IndexQueryBuilder {
    private final List<SearchIndex> requiredIndices;
    private final SearchIndex excludeIndex;

    public Collection<SearchIndex> getIndicies() {
        return requiredIndices;
    }

    public IndexQueryBuilder(List<SearchIndex> requiredIndices, SearchIndex excludeIndex) {
        this.requiredIndices = requiredIndices.stream().filter(Objects::nonNull).collect(Collectors.toList());
        this.excludeIndex = excludeIndex;
    }

    public Query build(IndexSearchBudget budget,
                       LongPredicate filter,
                       int wordId)
    {
        return new QueryForIndices(budget, filter, wordId);
    }

    // Special treatment for queries with few terms, prefer hits that appear in multiple buckets
    public Query buildUnderspecified(IndexSearchBudget budget, LongPredicate filter, int wordId) {

        if (requiredIndices.size() == 1) {
            return build(budget, filter, wordId);
        }

        var ranges = requiredIndices.stream().map(idx -> idx.rangeForWord(wordId)).toArray(SearchIndex.UrlIndexTree[]::new);
        var relevantIndices = IntStream.range(0, requiredIndices.size()).filter(i -> ranges[i].isPresent()).toArray();

        if (relevantIndices.length == 0) {
            return new QueryForIndices(budget, LongStream::empty);
        }
        else if (relevantIndices.length == 1 || relevantIndices[0] != 0) {
            return new QueryForIndices(budget, LongStream::empty);
        }

        var fstRange = requiredIndices.get(relevantIndices[0]).rangeForWord(wordId);

        LongStream priorityStream = underspecifiedPairStream(budget, 1000, relevantIndices[0], relevantIndices[0], wordId);
        for (int i = 1; i < relevantIndices.length; i++) {
            priorityStream = Streams.concat(priorityStream, underspecifiedPairStream(budget, 1000, relevantIndices[0], relevantIndices[i], wordId));
        }
        LongStream stream = LongStream.concat(priorityStream, fstRange.stream().takeWhile(budget::take)).filter(filter);

        return new QueryForIndices(budget, () -> stream);
    }

    private LongStream underspecifiedPairStream(IndexSearchBudget budget, int limit, int firstIdx, int otherIdx, int wordId) {
        SearchIndex firstTmp = requiredIndices.get(firstIdx),
                    secondTmp = requiredIndices.get(otherIdx);

        final SearchIndex fst;
        final SearchIndex snd;

        if (firstTmp.numUrls(wordId) > secondTmp.numUrls(wordId)) {
            fst = secondTmp;
            snd = firstTmp;
        }
        else {
            fst = firstTmp;
            snd = secondTmp;
        }

        var sndRange = snd.rangeForWord(wordId);
        var cache = sndRange.createIndexCache();

        return fst.rangeForWord(wordId).stream().takeWhile(budget::take).limit(limit).filter(data -> sndRange.hasUrl(cache, data));
    }



    private class QueryForIndices implements Query {
        private final Supplier<LongStream> supp;
        private final IndexSearchBudget budget;

        private QueryForIndices(IndexSearchBudget budget, LongPredicate filter, int wordId) {
            this.budget = budget;
            supp = () ->
                requiredIndices.stream().flatMapToLong(idx -> {
                    var range = idx.rangeForWord(wordId);
                    return range.stream().takeWhile(budget::take);
                })
                .filter(filter);
        }

        private QueryForIndices(IndexSearchBudget budget, Supplier<LongStream> supp) {
            this.budget = budget;
            this.supp = supp;
        }

        @Override
        public Query also(int wordId) {
            return new QueryForIndices(budget,
                    () -> requiredIndices.stream().flatMapToLong(idx -> alsoStream(idx, wordId)));
        }

        @Override
        public Query alsoCached(int wordId) {
            return new QueryForIndices(budget,
                    () -> requiredIndices.stream().flatMapToLong(idx -> alsoStreamCached(idx, wordId)));
        }

        @Override
        public Query not(int wordId) {
            // Happens when an index simply isn't present, won't find data anyway
            // so it's safe to no-op the query
            if (excludeIndex == null)
                return new QueryForIndices(budget, LongStream::empty);

            return new QueryForIndices(budget, () -> notStream(wordId));
        }

        private LongStream alsoStream(SearchIndex idx, int wordId) {
            var range = idx.rangeForWord(wordId);

            return stream().filter(range::hasUrl).takeWhile(budget::take);
        }

        private LongStream alsoStreamCached(SearchIndex idx, int wordId) {
            var range = idx.rangeForWord(wordId);
            var cache = range.createIndexCache();

            return stream().filter(data -> range.hasUrl(cache, data)).takeWhile(budget::take);
        }

        private LongStream notStream(int wordId) {
            var bodyRange = excludeIndex.rangeForWord(wordId);
            var cache = bodyRange.createIndexCache();

            return stream().filter(url -> !bodyRange.hasUrl(cache, url)).takeWhile(budget::take);
        }

        public LongStream stream() {
            return supp.get();
        }
    }
}
