package nu.marginalia.index.index;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Collections;
import java.util.List;

public record SearchIndexSearchTerms(
        LongList includes,
        LongList excludes,
        LongList priority,
        List<LongList> coherences
        )
{
    public SearchIndexSearchTerms() {
        this(LongList.of(), LongList.of(), LongList.of(), Collections.emptyList());
    }

    public boolean isEmpty() {
        return includes.isEmpty();
    }

    public long[] sortedDistinctIncludes(LongComparator comparator) {
        if (includes.isEmpty())
            return includes.toLongArray();

        LongList list = new LongArrayList(new LongOpenHashSet(includes));
        list.sort(comparator);
        return list.toLongArray();
    }

    public int size() {
        return includes.size() + excludes.size() + priority.size();
    }
}
