package nu.marginalia.wmsa.edge.search.results;

import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;

public class UrlDeduplicator {
    private final TIntHashSet seenSuperficialhashes = new TIntHashSet(200);
    private final TIntHashSet seenDataHashes = new TIntHashSet(200);
    private final TObjectIntHashMap<String> keyCount = new TObjectIntHashMap<>(200, 0.75f, 0);

    private final int resultsPerKey;
    public UrlDeduplicator(int resultsPerKey) {
        this.resultsPerKey = resultsPerKey;
    }

    public boolean shouldRemove(EdgeUrlDetails details) {
        return !filter(details);
    }
    public synchronized boolean filter(EdgeUrlDetails details) {
        if (!seenSuperficialhashes.add(details.getSuperficialHash())) {
            return false;
        }
        if (!seenDataHashes.add(details.getDataHash())) {
            return false;
        }
        final var domain = details.getUrl().getDomain();
        final String key;

        if (!details.isSpecialDomain()) {
            key = domain.getLongDomainKey();
        }
        else {
            key = domain.getDomainKey();
        }

        return keyCount.adjustOrPutValue(key, 1, 1) < resultsPerKey;
    }
}
