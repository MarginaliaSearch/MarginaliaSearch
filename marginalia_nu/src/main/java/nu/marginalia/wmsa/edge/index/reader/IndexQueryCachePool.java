package nu.marginalia.wmsa.edge.index.reader;

import nu.marginalia.util.btree.CachingBTreeReader;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class IndexQueryCachePool {
    private final Map<PoolKey, CachingBTreeReader.Cache> indexCaches = new HashMap<>();
    private final Map<RangeKey, SearchIndex.UrlIndexTree> rangeCache = new HashMap<>();
    private final Map<PoolKey, Integer> savedCounts = new HashMap<>();

    public CachingBTreeReader.Cache getIndexCache(SearchIndex index, SearchIndex.UrlIndexTree range) {
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
        long loadedBytes = indexCaches.values().stream().mapToLong(CachingBTreeReader.Cache::sizeBytes).sum();
        long savedBytes = savedCounts.entrySet().stream().mapToLong(e -> e.getValue() * indexCaches.get(e.getKey()).sizeBytes()).sum();

        long loaded = indexCaches.values().stream().filter(CachingBTreeReader.Cache::isLoaded).count();

        logger.info("Index Cache Summary: {}/{} loaded/total, {} index blocks loaded, {} index blocks saved", loaded, indexCaches.size(), loadedBytes/4096., savedBytes/4096.);
    }

    public void clear() {
        indexCaches.clear();
    }

    public SearchIndex.UrlIndexTree getRange(IndexWordsTable words, int wordId) {
        return rangeCache.get(new RangeKey(words, wordId));
    }

    public void cacheRange(IndexWordsTable words, int wordId, SearchIndex.UrlIndexTree range) {
        rangeCache.put(new RangeKey(words, wordId), range);
    }

    private record RangeKey(IndexWordsTable table, int wordId) {}
    private record PoolKey(SearchIndex index, long dataOffset) {}
}
