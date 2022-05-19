package nu.marginalia.wmsa.edge.search.results;

import com.google.common.base.Strings;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;

public class UrlDeduplicator {
    private final TIntHashSet seenSuperficialhashes = new TIntHashSet(100);
    private final TIntHashSet seenDataHashes = new TIntHashSet(100);
    private final TObjectIntHashMap<String> ipCount = new TObjectIntHashMap<>(100, 0.75f, 0);

    private final int resultsPerIp;
    public UrlDeduplicator(int resultsPerIp) {
        this.resultsPerIp = resultsPerIp;
    }

    public synchronized boolean filter(EdgeUrlDetails details) {
        if (!seenSuperficialhashes.add(details.getSuperficialHash())) {
            return false;
        }
        if (!seenDataHashes.add(details.getDataHash())) {
            return false;
        }
        if (Strings.isNullOrEmpty(details.getIp())) {
            final var domain = details.getUrl().getDomain();
            final String key;

            if (!details.isSpecialDomain()) {
                key = domain.getLongDomainKey();
            }
            else {
                key = domain.getDomainKey();
            }

            return ipCount.adjustOrPutValue(key, 1, 1) <= resultsPerIp;
        }

        return ipCount.adjustOrPutValue(details.getIp(), 1, 1) < resultsPerIp;
    }
}
