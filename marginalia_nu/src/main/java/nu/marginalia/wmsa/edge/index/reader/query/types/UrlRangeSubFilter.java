package nu.marginalia.wmsa.edge.index.reader.query.types;

import nu.marginalia.util.btree.CachingBTreeReader;
import nu.marginalia.wmsa.edge.index.reader.IndexQueryCachePool;
import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import org.jetbrains.annotations.Nullable;

public record UrlRangeSubFilter(SearchIndex source, SearchIndex.UrlIndexTree range, CachingBTreeReader.Cache cache) implements QueryFilterStep {

    public UrlRangeSubFilter(SearchIndex source, SearchIndex.UrlIndexTree range, IndexQueryCachePool pool) {
        this(source, range, pool.getIndexCache(source, range));
    }

    @Nullable
    @Override
    public SearchIndex getIndex() {
        return source;
    }

    public boolean test(long id) {
        return range.hasUrl(cache, id);
    }

    @Override
    public double cost() {
        return cache.getIndexedDataSize();
    }

    @Override
    public String describe() {
        return "UrlRange["+getIndex().name+"]";
    }
}
