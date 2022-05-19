package nu.marginalia.wmsa.edge.index.service.index;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.service.query.IndexQueryBuilder;
import nu.marginalia.wmsa.edge.index.service.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.index.service.query.Query;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;
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

        queryBuilders.put(IndexBlock.Words, new IndexQueryBuilder(Stream.of(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, topIndex, midIndex, lowIndex, namesIndex, wordsIndex).collect(Collectors.toList()), wordsIndex));
        queryBuilders.put(IndexBlock.Low, new IndexQueryBuilder(Stream.of(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, topIndex, midIndex, lowIndex, namesIndex).collect(Collectors.toList()), wordsIndex));
        queryBuilders.put(IndexBlock.Middle, new IndexQueryBuilder(Stream.of(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, topIndex, midIndex).collect(Collectors.toList()), wordsIndex));
        queryBuilders.put(IndexBlock.Top, new IndexQueryBuilder(Stream.of(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, topIndex).collect(Collectors.toList()), wordsIndex));
        queryBuilders.put(IndexBlock.PositionWords, new IndexQueryBuilder(Stream.of(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, namesIndex, positionIndex).collect(Collectors.toList()), wordsIndex));
        queryBuilders.put(IndexBlock.NamesWords, new IndexQueryBuilder(Stream.of(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, namesIndex).collect(Collectors.toList()), wordsIndex));
        queryBuilders.put(IndexBlock.Link, new IndexQueryBuilder(Stream.of(metaIndex, titleKeywordsIndex, topicIndex, titleIndex, linkIndex).collect(Collectors.toList()), wordsIndex));
        queryBuilders.put(IndexBlock.Title, new IndexQueryBuilder(Stream.of(metaIndex, titleKeywordsIndex, topicIndex, titleIndex).collect(Collectors.toList()), wordsIndex));
        queryBuilders.put(IndexBlock.TitleKeywords, new IndexQueryBuilder(Stream.of(metaIndex, titleKeywordsIndex).collect(Collectors.toList()), wordsIndex));

        underspecifiedQueryBuilders.put(IndexBlock.TitleKeywords, new IndexQueryBuilder(Stream.of(titleKeywordsIndex, linkIndex, topicIndex, topIndex, midIndex, lowIndex, namesIndex, positionIndex, metaIndex).collect(Collectors.toList()), wordsIndex));
        underspecifiedQueryBuilders.put(IndexBlock.Link, new IndexQueryBuilder(Stream.of(linkIndex, topicIndex, topIndex, midIndex, lowIndex, namesIndex, positionIndex, metaIndex).collect(Collectors.toList()), wordsIndex));
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
