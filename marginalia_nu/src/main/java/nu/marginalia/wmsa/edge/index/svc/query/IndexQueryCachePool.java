package nu.marginalia.wmsa.edge.index.svc.query;

import nu.marginalia.util.btree.CachingBTreeReader;
import nu.marginalia.wmsa.edge.index.reader.IndexWordsTable;
import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class IndexQueryCachePool {
    private final Map<PoolKey, CachingBTreeReader.BTreeCachedIndex> indexCaches = new HashMap<>();
    private final Map<RangeKey, SearchIndex.IndexBTreeRange> rangeCache = new HashMap<>();
    private final Map<PoolKey, Integer> savedCounts = new HashMap<>();

    public CachingBTreeReader.BTreeCachedIndex getIndexCache(SearchIndex index, SearchIndex.IndexBTreeRange range) {
        var key = new PoolKey(index, range.dataOffset);
        var entry = indexCaches.get(key);

        if (entry == null) {
            entry = range.createIndexCache();
            indexCaches.put(key, entry);
        }
        else {
            savedCounts.merge(key, 1, Integer::sum);
        }

        return entry;
    }


    public boolean isUrlPresent(SearchIndex index, int term, long url) {
        var range = index.rangeForWord(this, term);
        return range.isPresent() && range.hasUrl(this, url);
    }

    public void printSummary(Logger logger) {
        long loadedBytes = indexCaches.values().stream().mapToLong(CachingBTreeReader.BTreeCachedIndex::sizeBytes).sum();
        long savedBytes = savedCounts.entrySet().stream().mapToLong(e -> e.getValue() * indexCaches.get(e.getKey()).sizeBytes()).sum();

        long loaded = indexCaches.values().stream().filter(CachingBTreeReader.BTreeCachedIndex::isLoaded).count();

        logger.info("Index Cache Summary: {}/{} loaded/total, {} index blocks loaded, {} index blocks saved", loaded, indexCaches.size(), loadedBytes/4096., savedBytes/4096.);
    }

    public void clear() {
        indexCaches.clear();
    }

    public SearchIndex.IndexBTreeRange getRange(IndexWordsTable words, int wordId) {
        return rangeCache.get(new RangeKey(words, wordId));
    }

    public void cacheRange(IndexWordsTable words, int wordId, SearchIndex.IndexBTreeRange range) {
        rangeCache.put(new RangeKey(words, wordId), range);
    }

    private record RangeKey(IndexWordsTable table, int wordId) {}
    private record PoolKey(SearchIndex index, long dataOffset) {}
}
