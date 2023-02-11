package nu.marginalia.wmsa.edge.index.svc.searchset;

import gnu.trove.set.hash.TIntHashSet;

import java.util.Arrays;
import java.util.Collection;

public class SmallSearchSet implements SearchSet {
    public TIntHashSet entries;

    public SmallSearchSet(Collection<Integer> domains) {
        entries = new TIntHashSet(domains);
    }

    @Override
    public boolean contains(int domainId) {
        return entries.contains(domainId);
    }

    public String toString() {
        return getClass().getSimpleName() + Arrays.toString(entries.toArray());
    }

}
