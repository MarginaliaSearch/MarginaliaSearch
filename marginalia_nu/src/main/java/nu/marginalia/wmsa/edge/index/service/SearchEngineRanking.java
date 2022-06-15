package nu.marginalia.wmsa.edge.index.service;

import gnu.trove.list.TIntList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

import static nu.marginalia.wmsa.edge.index.EdgeIndexService.DYNAMIC_BUCKET_LENGTH;

public class SearchEngineRanking {

    private final TIntIntHashMap domainToId
            = new TIntIntHashMap(1_000_000, 0.5f, -1, Integer.MAX_VALUE);

    private final TIntHashSet[] domainToBucket = new TIntHashSet[DYNAMIC_BUCKET_LENGTH+1];

    private final int offset;
    private final double[] limits;

    public SearchEngineRanking(int offset, TIntList domains, double... limits) {
        this.offset = offset;
        this.limits = limits;

        for (int i = offset; i < offset+limits.length; i++) {
            domainToBucket[i] = new TIntHashSet(100, 0.5f, DYNAMIC_BUCKET_LENGTH);
        }

        for (int i = 0; i < domains.size(); i++) {
            double relPortion = i / (double) domains.size();

            for (int limit = 0; limit < limits.length; limit++) {
                if (relPortion < limits[limit]) {
                    domainToBucket[limit+offset].add(domains.get(i));
                    break;
                }
            }

            domainToId.put(domains.get(i), i);
        }
    }

    public boolean ownsBucket(int bucketId) {
        return bucketId >= offset && bucketId < offset + limits.length;
    }

    public boolean hasBucket(int bucket, int domain) {
        var set = domainToBucket[bucket];
        if (set == null) {
            return false;
        }
        return set.contains(domain);
    }

    public int translateId(int id) {
        return domainToId.get(id);
    }
}
