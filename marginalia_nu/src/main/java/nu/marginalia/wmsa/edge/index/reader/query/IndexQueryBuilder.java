package nu.marginalia.wmsa.edge.index.reader.query;

import com.google.common.collect.Streams;
import nu.marginalia.wmsa.edge.index.reader.SearchIndex;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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
                       int wordId) {
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
            return build(budget, filter, wordId);
        }

        var fstRange = requiredIndices.get(relevantIndices[0]).rangeForWord(wordId);

        return new QueryForIndices(budget, () ->
            Streams.concat(IntStream.range(1, relevantIndices.length)
                            .mapToObj(i -> underspecifiedPairStream(budget, 1000, relevantIndices[0], relevantIndices[i], wordId))
                            .flatMapToLong(Function.identity()),
                    fstRange.stream().takeWhile(budget::take))
                .filter(filter)
        );
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

        return fst.rangeForWord(wordId).stream().takeWhile(budget::take).limit(limit).filter(
                url -> snd.hasUrl(url, sndRange)
        );
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
        public Query not(int wordId) {
            return new QueryForIndices(budget, () -> notStream(wordId));
        }

        private LongStream alsoStream(SearchIndex idx, int wordId) {
            var range = idx.rangeForWord(wordId);

            return stream().filter(url -> idx.hasUrl(url, range)).takeWhile(budget::take);
        }

        private LongStream notStream(int wordId) {
            var bodyRange = excludeIndex.rangeForWord(wordId);
            return stream().filter(url -> !excludeIndex.hasUrl(url, bodyRange)).takeWhile(budget::take);
        }

        public LongStream stream() {
            return supp.get();
        }
    }
}
