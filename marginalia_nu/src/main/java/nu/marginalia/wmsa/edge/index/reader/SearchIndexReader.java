package nu.marginalia.wmsa.edge.index.reader;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.svc.query.IndexDomainQueryFactory;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQuery;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQueryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class SearchIndexReader implements AutoCloseable {

    private final EnumMap<IndexBlock, SearchIndex> indices;
    private final EnumMap<IndexBlock, IndexQueryFactory> queryBuilders;
    private final IndexDomainQueryFactory domainQueryFactory;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchIndexReader(
            EnumMap<IndexBlock, SearchIndex> indices) {
        this.indices = indices;

        var linkIndex  = indices.get(IndexBlock.Link);
        var titleIndex  = indices.get(IndexBlock.Title);
        var metaIndex  = indices.get(IndexBlock.Meta);

        var words1  = indices.get(IndexBlock.Words_1);
        var words2  = indices.get(IndexBlock.Words_2);
        var words4  = indices.get(IndexBlock.Words_4);
        var words8  = indices.get(IndexBlock.Words_8);
        var words16  = indices.get(IndexBlock.Words_16Plus);
        var artifacts  = indices.get(IndexBlock.Artifacts);

        queryBuilders = new EnumMap<>(IndexBlock.class);

        List<SearchIndex> excludeIndices = listOfNonNulls(metaIndex, titleIndex, words1, words2, words4, words8, words16);

        queryBuilders.put(IndexBlock.Title, new IndexQueryFactory(listOfNonNulls(metaIndex, titleIndex, linkIndex), excludeIndices));
        queryBuilders.put(IndexBlock.Words_1, new IndexQueryFactory(listOfNonNulls(metaIndex, words1), excludeIndices));
        queryBuilders.put(IndexBlock.Words_2, new IndexQueryFactory(listOfNonNulls(metaIndex, words2), excludeIndices));
        queryBuilders.put(IndexBlock.Words_4, new IndexQueryFactory(listOfNonNulls(metaIndex, words4), excludeIndices));
        queryBuilders.put(IndexBlock.Words_8, new IndexQueryFactory(listOfNonNulls(metaIndex, words8), excludeIndices));
        queryBuilders.put(IndexBlock.Words_16Plus, new IndexQueryFactory(listOfNonNulls(metaIndex, words16, artifacts), excludeIndices));

        domainQueryFactory = new IndexDomainQueryFactory(indices.get(IndexBlock.Words_1));
    }

    @SafeVarargs
    public final <T> List<T> listOfNonNulls(T... vals) {
        return Stream.of(vals).filter(Objects::nonNull).toList();
    }


    public IndexQueryFactory.IndexQueryBuilder findWord(IndexBlock block, Integer quality, int wordId) {
        var builder = queryBuilders.get(block);

        if (builder == null)
            return null;

        if (quality == null) {
            return builder.buildQuery(wordId);
        }
        else {
            return builder.buildQuery(quality, wordId);
        }
    }

    public IndexQueryFactory.IndexQueryBuilder findWordForDomainList(IndexBlock block, List<Integer> domains, int wordId) {
        var builder = queryBuilders.get(block);

        if (builder == null)
            return null;

        return builder.buildQuery(domains, wordId);
    }

    public IndexQuery findDomain(int wordId) {
        return domainQueryFactory.buildQuery(wordId);
    }

    @Override
    public void close() throws Exception {
        for (var idx : indices.values()) {
            idx.close();
        }
    }

    @SneakyThrows
    public long numHits(IndexBlock block, int word) {
        IndexQueryFactory builder = queryBuilders.get(block);

        if (builder == null)
            return 0L;

        long hits = 0;
        for (var index : builder.getIndicies()) {
            hits += index.numUrls(word);
        }
        return hits;
    }


    public long[] getMetadata(IndexBlock block, int termId, long[] ids) {
        final var index = indices.get(block);
        if (null == index) {
            return new long[ids.length];
        }

        return indices.get(block).rangeForWord(termId).getMetadata(ids);
    }
}
