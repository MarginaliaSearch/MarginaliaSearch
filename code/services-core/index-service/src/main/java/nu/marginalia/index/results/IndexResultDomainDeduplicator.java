package nu.marginalia.index.results;

import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.index.client.model.results.SearchResultItem;

public class IndexResultDomainDeduplicator {
    final TLongIntMap resultsByRankingId = new TLongIntHashMap(2048, 0.5f, -1, 0);
    final int limitByDomain;

    public IndexResultDomainDeduplicator(int limitByDomain) {
        this.limitByDomain = limitByDomain;
    }

    public boolean test(SearchResultItem item) {
        final long key = item.deduplicationKey();
        if (key == 0)
            return true;

        return resultsByRankingId.adjustOrPutValue(key, 1, 1) <= limitByDomain;
    }

    public int getCount(SearchResultItem item) {
        final long key = item.deduplicationKey();
        if (key == 0)
            return 1;

        return resultsByRankingId.get(key);
    }
}
