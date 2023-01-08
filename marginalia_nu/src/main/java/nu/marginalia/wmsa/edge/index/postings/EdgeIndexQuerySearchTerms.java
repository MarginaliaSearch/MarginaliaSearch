package nu.marginalia.wmsa.edge.index.postings;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public record EdgeIndexQuerySearchTerms(IntList includes, IntList excludes, IntList priority) {
    public EdgeIndexQuerySearchTerms() {
        this(IntList.of(), IntList.of(), IntList.of());
    }

    public boolean isEmpty() {
        return includes.isEmpty();
    }

    public int[] sortedDistinctIncludes(IntComparator comparator) {
        if (includes.isEmpty())
            return includes.toIntArray();

        IntList list = new IntArrayList(new IntOpenHashSet(includes));
        list.sort(comparator);
        return list.toIntArray();
    }
}
