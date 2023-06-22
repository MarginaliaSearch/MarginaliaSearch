package nu.marginalia.index.svc.searchset;

import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.index.searchset.SearchSet;

import java.util.Arrays;
import java.util.Collection;

/** A specialized search set for a small number of entries, for use when specifying the exact domains to query */
public class SmallSearchSet implements SearchSet {
    public TIntHashSet entries;

    public SmallSearchSet(Collection<Integer> domains) {
        entries = new TIntHashSet(domains);
    }

    @Override
    public boolean contains(int domainId, long meta) {
        return entries.contains(domainId);
    }

    public String toString() {
        return getClass().getSimpleName() + Arrays.toString(entries.toArray());
    }

}
