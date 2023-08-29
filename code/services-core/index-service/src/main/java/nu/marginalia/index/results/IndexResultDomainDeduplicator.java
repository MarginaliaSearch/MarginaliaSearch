package nu.marginalia.index.results;

import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.index.client.model.results.SearchResultItem;

public class IndexResultDomainDeduplicator {
    final TLongIntMap resultsByDomainId = CachedObjects.getMap();
    final int limitByDomain;

    public IndexResultDomainDeduplicator(int limitByDomain) {
        this.limitByDomain = limitByDomain;
    }

    public boolean test(SearchResultItem item) {
        final long key = item.getDomainId();

        return resultsByDomainId.adjustOrPutValue(key, 1, 1) <= limitByDomain;
    }

    public int getCount(SearchResultItem item) {
        final long key = item.getDomainId();

        return resultsByDomainId.get(key);
    }

    private static class CachedObjects {
        private static final ThreadLocal<TLongIntHashMap> mapCache = ThreadLocal.withInitial(() ->
                new TLongIntHashMap(2048, 0.5f, -1, 0)
        );

        private static TLongIntHashMap getMap() {
            var ret = mapCache.get();
            ret.clear();
            return ret;
        }

        public static void clear() {
            mapCache.remove();
        }
    }

    static void clearCachedObjects() {
        CachedObjects.clear();
    }
}

