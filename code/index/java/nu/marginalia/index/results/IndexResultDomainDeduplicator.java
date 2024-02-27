package nu.marginalia.index.results;

import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;

public class IndexResultDomainDeduplicator {
    final TLongIntMap resultsByDomainId = new TLongIntHashMap(2048, 0.5f, -1, 0);
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
}

