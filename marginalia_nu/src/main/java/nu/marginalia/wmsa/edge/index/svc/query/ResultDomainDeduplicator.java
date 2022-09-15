package nu.marginalia.wmsa.edge.index.svc.query;

import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultItem;

public class ResultDomainDeduplicator {
    final TLongIntMap resultsByRankingId = new TLongIntHashMap(2048, 0.5f, -1, 0);
    final int limitByDomain;

    public ResultDomainDeduplicator(int limitByDomain) {
        this.limitByDomain = limitByDomain;
    }

    public boolean filterRawValue(long value) {
        int rankingId = (int) (value >>> 32);

        if (rankingId == Integer.MAX_VALUE) {
            return true;
        }

        return resultsByRankingId.get(getKey(rankingId)) <= limitByDomain;
    }

    long getKey(int rankingId) {
        return rankingId;
    }

    public boolean test(long value) {
        int ranking = (int) (value >>> 32);
        if (ranking == Integer.MAX_VALUE) {
            return true;
        }

        return resultsByRankingId.adjustOrPutValue(ranking, 1, 1) <= limitByDomain;
    }

    public boolean test(EdgeSearchResultItem item) {
        final long key = item.deduplicationKey();
        if (key == 0)
            return true;

        return resultsByRankingId.adjustOrPutValue(key, 1, 1) <= limitByDomain;
    }

    public int getCount(EdgeSearchResultItem item) {
        final long key = item.deduplicationKey();
        if (key == 0)
            return 1;

        return resultsByRankingId.get(key);
    }
}
