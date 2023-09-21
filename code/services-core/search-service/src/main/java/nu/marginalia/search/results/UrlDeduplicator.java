package nu.marginalia.search.results;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.search.model.UrlDetails;
import nu.marginalia.lsh.EasyLSH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrlDeduplicator {
    private final int LSH_SIMILARITY_THRESHOLD = 2;
    private static final Logger logger = LoggerFactory.getLogger(UrlDeduplicator.class);

    private final TIntHashSet seenSuperficialhashes = new TIntHashSet(200);
    private final TLongList seehLSHList = new TLongArrayList(200);
    private final TObjectIntHashMap<String> keyCount = new TObjectIntHashMap<>(200, 0.75f, 0);

    private final int resultsPerKey;
    public UrlDeduplicator(int resultsPerKey) {
        this.resultsPerKey = resultsPerKey;
    }

    public synchronized boolean shouldRemove(UrlDetails details) {
        if (!deduplicateOnSuperficialHash(details))
            return true;
        if (!deduplicateOnLSH(details))
            return true;
        if (!limitResultsPerDomain(details))
            return true;

        return false;
    }

    private boolean deduplicateOnSuperficialHash(UrlDetails details) {
        return seenSuperficialhashes.add(details.getSuperficialHash());
    }

    private boolean deduplicateOnLSH(UrlDetails details) {
        long thisHash = details.dataHash;

        if (0 == thisHash)
            return true;

        if (seehLSHList.forEach(otherHash -> EasyLSH.hammingDistance(thisHash, otherHash) >= LSH_SIMILARITY_THRESHOLD))
        {
            seehLSHList.add(thisHash);
            return true;
        }

        return false;

    }

    private boolean limitResultsPerDomain(UrlDetails details) {
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
