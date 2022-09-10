package nu.marginalia.wmsa.edge.index.reader;

import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultItem;

import java.util.List;

public class ResultDomainDeduplicator {
    final TLongIntMap resultsByRankingId = new TLongIntHashMap(2048, 0.5f, -1, 0);
    final int limitByDomain;

    public ResultDomainDeduplicator(int limitByDomain) {
        this.limitByDomain = limitByDomain;
    }

    public boolean filterRawValue(int bucket, long value) {
        int domain = (int) (value >>> 32);

        if (domain == Integer.MAX_VALUE) {
            return true;
        }

        return resultsByRankingId.get(getKey(bucket, domain)) <= limitByDomain;
    }

    long getKey(int bucketId, int rankingId) {
        return ((long) bucketId) << 32 | rankingId;
    }

    long getKey(EdgeSearchResultItem item) {
        return ((long) item.bucketId) << 32 | item.getRanking();
    }

    public boolean test(EdgeSearchResultItem item) {
        if (item.getRanking() == Integer.MAX_VALUE) {
            return true;
        }

        return resultsByRankingId.adjustOrPutValue(getKey(item), 1, 1) <= limitByDomain;
    }

    public void addAll(List<EdgeSearchResultItem> items) {
        for (var item : items) {
            resultsByRankingId.adjustOrPutValue(getKey(item), 1, 1);
        }
    }

    public void add(EdgeSearchResultItem item) {
        resultsByRankingId.adjustOrPutValue(getKey(item), 1, 1);
    }
}
