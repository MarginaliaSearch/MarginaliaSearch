package nu.marginalia.wmsa.edge.index.svc.query.types.filter;

import nu.marginalia.util.btree.CachingBTreeReader;
import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import nu.marginalia.wmsa.edge.index.svc.query.IndexQueryCachePool;
import org.jetbrains.annotations.Nullable;

public record QueryFilterBTreeRange(SearchIndex source, SearchIndex.IndexBTreeRange range, CachingBTreeReader.BTreeCachedIndex cache) implements QueryFilterStepIf {

    public QueryFilterBTreeRange(SearchIndex source, SearchIndex.IndexBTreeRange range, IndexQueryCachePool pool) {
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
