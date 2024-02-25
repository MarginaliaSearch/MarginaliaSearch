package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;

import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.index.model.SearchTermsUtil.getWordId;

public record SearchTerms(
        LongList includes,
        LongList excludes,
        LongList priority,
        List<LongList> coherences
        )
{
    public SearchTerms(SearchSubquery subquery) {
        this(new LongArrayList(),
             new LongArrayList(),
             new LongArrayList(),
             new ArrayList<>());

        for (var word : subquery.searchTermsInclude) {
            includes.add(getWordId(word));
        }
        for (var word : subquery.searchTermsAdvice) {
            // This looks like a bug, but it's not
            includes.add(getWordId(word));
        }


        for (var coherence : subquery.searchTermCoherences) {
            LongList parts = new LongArrayList(coherence.size());

            for (var word : coherence) {
                parts.add(getWordId(word));
            }

            coherences.add(parts);
        }

        for (var word : subquery.searchTermsExclude) {
            excludes.add(getWordId(word));
        }
        for (var word : subquery.searchTermsPriority) {
            priority.add(getWordId(word));
        }
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
