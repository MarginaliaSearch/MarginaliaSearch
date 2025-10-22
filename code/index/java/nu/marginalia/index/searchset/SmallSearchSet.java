package nu.marginalia.index.searchset;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.Arrays;
import java.util.Collection;

/** A specialized search set for a small number of entries, for use when specifying the exact domains to query */
public class SmallSearchSet implements SearchSet {
    public IntSet set;

    public SmallSearchSet(Collection<Integer> domains) {
        set = new IntOpenHashSet(domains);
    }

    @Override
    public boolean contains(int domainId) {
        return set.contains(domainId);
    }

    public String toString() {
        return getClass().getSimpleName() + Arrays.toString(set.toArray());
    }

    @Override
    public IntList domainIds() {
        IntList ret = new IntArrayList(set);
        ret.sort(Integer::compareTo);
        return ret;
    }
}
