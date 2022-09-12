package nu.marginalia.wmsa.edge.index.reader;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQueryCachePool;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQueryFactory;
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
    private final EnumMap<IndexBlock, IndexQueryFactory> queryBuilders;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final IndexBlock[] indicesBySearchOrder = new IndexBlock[] {
            IndexBlock.Title,
            IndexBlock.Tfidf_Top,
            IndexBlock.Tfidf_Middle,
            IndexBlock.Tfidf_Lower,
            IndexBlock.Words_1,
            IndexBlock.Words_2,
            IndexBlock.Words_4,
            IndexBlock.Words_8,
            IndexBlock.Words_16Plus,
    };

    @Inject
    public SearchIndexReader(
            EnumMap<IndexBlock, SearchIndex> indices) {
        this.indices = indices;

        var lowIndex  = indices.get(IndexBlock.Tfidf_Lower);
        var midIndex  = indices.get(IndexBlock.Tfidf_Middle);
        var topIndex  = indices.get(IndexBlock.Tfidf_Top);
        var linkIndex  = indices.get(IndexBlock.Link);
        var titleIndex  = indices.get(IndexBlock.Title);
        var siteIndex  = indices.get(IndexBlock.Site);
        var metaIndex  = indices.get(IndexBlock.Meta);
        var topicIndex  = indices.get(IndexBlock.Subjects);

        var words1  = indices.get(IndexBlock.Words_1);
        var words2  = indices.get(IndexBlock.Words_2);
        var words4  = indices.get(IndexBlock.Words_4);
        var words8  = indices.get(IndexBlock.Words_8);
        var words16  = indices.get(IndexBlock.Words_16Plus);
        var artifacts  = indices.get(IndexBlock.Artifacts);

        queryBuilders = new EnumMap<>(IndexBlock.class);

        List<SearchIndex> excludeIndices = listOfNonNulls(metaIndex, titleIndex, topIndex, midIndex, lowIndex, words1);
        List<SearchIndex> priorityIndices = listOfNonNulls(titleIndex, linkIndex, siteIndex, topIndex, topicIndex);

        queryBuilders.put(IndexBlock.Title, new IndexQueryFactory(listOfNonNulls(metaIndex, titleIndex, linkIndex), excludeIndices, priorityIndices));
        queryBuilders.put(IndexBlock.Words_1, new IndexQueryFactory(listOfNonNulls(metaIndex, words1), excludeIndices, priorityIndices));
        queryBuilders.put(IndexBlock.Words_2, new IndexQueryFactory(listOfNonNulls(metaIndex, words2), excludeIndices, priorityIndices));
        queryBuilders.put(IndexBlock.Words_4, new IndexQueryFactory(listOfNonNulls(metaIndex, words4), excludeIndices, priorityIndices));
        queryBuilders.put(IndexBlock.Words_8, new IndexQueryFactory(listOfNonNulls(metaIndex, words8), excludeIndices, priorityIndices));
        queryBuilders.put(IndexBlock.Words_16Plus, new IndexQueryFactory(listOfNonNulls(metaIndex, words16, artifacts), excludeIndices, priorityIndices));
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

    public IndexQueryFactory.IndexQueryBuilder findWord(IndexQueryCachePool cachePool, IndexBlock block, int wordId) {
        var builder = queryBuilders.get(block);

        if (builder == null)
            return null;

        return builder.buildQuery(cachePool, wordId);
    }

    @Override
    public void close() throws Exception {
        for (var idx : indices.values()) {
            idx.close();
        }
    }

    @SneakyThrows
    public long numHits(IndexQueryCachePool pool, IndexBlock block, int word) {
        IndexQueryFactory builder = queryBuilders.get(block);

        if (builder == null)
            return 0L;

        long hits = 0;
        for (var index : builder.getIndicies()) {
            hits += index.numUrls(pool, word);
        }
        return hits;
    }

    public IndexBlock getBlockForResult(IndexQueryCachePool cachePool, int searchTerm, long urlId) {
        for (var block : indicesBySearchOrder) {
            var index = indices.get(block);

            if (null == index) {
                continue;
            }

            if (cachePool.isUrlPresent(index, searchTerm, urlId))
                return block;

        }

        return IndexBlock.Words_16Plus;
    }

    public boolean isTermInBucket(IndexQueryCachePool cachePool, IndexBlock block, int searchTerm, long urlId) {
        final var index = indices.get(block);
        if (null == index) return false;

        return cachePool.isUrlPresent(index, searchTerm, urlId);
    }
}
