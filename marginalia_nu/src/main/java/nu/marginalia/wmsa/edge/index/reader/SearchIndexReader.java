package nu.marginalia.wmsa.edge.index.reader;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.query.IndexQueryBuilder;
import nu.marginalia.wmsa.edge.index.reader.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.index.reader.query.Query;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class SearchIndexReader implements AutoCloseable {

    private final EnumMap<IndexBlock, SearchIndex> indices;

    private final EnumMap<IndexBlock, IndexQueryBuilder> queryBuilders;
    private final EnumMap<IndexBlock, IndexQueryBuilder> underspecifiedQueryBuilders;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Cache<Pair<IndexBlock, Integer>, Long> numHitsCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private static final IndexBlock[] indicesBySearchOrder = new IndexBlock[] {
            IndexBlock.Top,
            IndexBlock.Middle,
            IndexBlock.Low,
            IndexBlock.Words,
            IndexBlock.NamesWords,
    };

    @Inject
    public SearchIndexReader(
            EnumMap<IndexBlock, SearchIndex> indices) {
        this.indices = indices;

        var lowIndex  = indices.get(IndexBlock.Low);
        var midIndex  = indices.get(IndexBlock.Middle);
        var topIndex  = indices.get(IndexBlock.Top);
        var linkIndex  = indices.get(IndexBlock.Link);
        var titleIndex  = indices.get(IndexBlock.Title);
        var namesIndex  = indices.get(IndexBlock.NamesWords);
        var positionIndex  = indices.get(IndexBlock.PositionWords);
        var titleKeywordsIndex  = indices.get(IndexBlock.TitleKeywords);
        var wordsIndex  = indices.get(IndexBlock.Words);
        var metaIndex  = indices.get(IndexBlock.Meta);
        var topicIndex  = indices.get(IndexBlock.Topic);

        queryBuilders = new EnumMap<>(IndexBlock.class);
        underspecifiedQueryBuilders = new EnumMap<>(IndexBlock.class);

        queryBuilders.put(IndexBlock.Words, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, topIndex, midIndex, lowIndex, namesIndex, wordsIndex), wordsIndex));
        queryBuilders.put(IndexBlock.Low, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, topIndex, midIndex, lowIndex, namesIndex), wordsIndex));
        queryBuilders.put(IndexBlock.Middle, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, topIndex, midIndex), wordsIndex));
        queryBuilders.put(IndexBlock.Top, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, topIndex), wordsIndex));
        queryBuilders.put(IndexBlock.PositionWords, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, namesIndex, positionIndex), wordsIndex));
        queryBuilders.put(IndexBlock.NamesWords, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, namesIndex), wordsIndex));
        queryBuilders.put(IndexBlock.Link, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, linkIndex), wordsIndex));
        queryBuilders.put(IndexBlock.Title, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex, topicIndex, titleIndex), wordsIndex));
        queryBuilders.put(IndexBlock.TitleKeywords, new IndexQueryBuilder(listOfNonNulls(metaIndex, titleKeywordsIndex), wordsIndex));

        underspecifiedQueryBuilders.put(IndexBlock.TitleKeywords, new IndexQueryBuilder(listOfNonNulls(titleKeywordsIndex, linkIndex, topicIndex, topIndex, midIndex, lowIndex, namesIndex, positionIndex, metaIndex), wordsIndex));
        underspecifiedQueryBuilders.put(IndexBlock.Link, new IndexQueryBuilder(listOfNonNulls(linkIndex, topicIndex, topIndex, midIndex, lowIndex, namesIndex, positionIndex, metaIndex), wordsIndex));
    }

    @SafeVarargs
    public final <T> List<T> listOfNonNulls(T... vals) {
        return Stream.of(vals).filter(Objects::nonNull).toList();
    }


    public LongStream findHotDomainsForKeyword(IndexBlock block, int wordId, int queryDepth, int minHitCount, int maxResults) {
        var index = indices.get(block);

        if (index == null)
            return LongStream.empty();

        return index.rangeForWord(wordId)
                .stream()
                .limit(queryDepth)
                .filter(new LongPredicate() {
                    long last = Long.MIN_VALUE;
                    int count = 0;

                    @Override
                    public boolean test(long value) {
                        if ((last >>> 32L) == (value >>> 32L)) {
                            return count++ == minHitCount;
                        }
                        else {
                            last = value;
                            count = 0;

                        }
                        return false;
                    }
                })
                .limit(maxResults);
    }

    public Query findUnderspecified(
            IndexBlock block,
            IndexSearchBudget budget,
            LongPredicate filter,
            int wordId) {

        var builder = underspecifiedQueryBuilders.get(block);

        if (null != builder) {
            return builder.buildUnderspecified(budget, filter, wordId);
        }
        return findWord(block, budget, filter, wordId);
    }

    public Query findWord(IndexBlock block, IndexSearchBudget budget, LongPredicate filter, int wordId) {
        return queryBuilders.get(block).build(budget, filter, wordId);
    }

    @Override
    public void close() throws Exception {
        for (var idx : indices.values()) {
            idx.close();
        }
        numHitsCache.invalidateAll();
        numHitsCache.cleanUp();
    }

    @SneakyThrows
    public long numHits(IndexBlock block, int word) {
        return numHitsCache.get(Pair.of(block, word),
                () -> queryBuilders.get(block)
                        .getIndicies()
                        .stream()
                        .mapToLong(idx -> idx.numUrls(word))
                        .sum()
        );
    }

    public IndexBlock getBlockForResult(int searchTerm, long urlId) {
        for (var block : indicesBySearchOrder) {
            var index = indices.get(block);

            if (null == index) {
                continue;
            }

            var range = index.rangeForWord(searchTerm);

            if (index.hasUrl(urlId, range)) {
                return block;
            }
        }
        return IndexBlock.Words;
    }

    public boolean isTermInBucket(IndexBlock block, int searchTerm, long urlId) {
        final var index = indices.get(block);
        if (null == index) return false;

        final var range = index.rangeForWord(searchTerm);

        return index.hasUrl(urlId, range);
    }
}
