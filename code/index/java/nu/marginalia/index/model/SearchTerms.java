package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static nu.marginalia.index.model.SearchTermsUtil.getWordId;

public final class SearchTerms {
    private final LongList includes;
    private final LongList excludes;
    private final LongList priority;
    private final List<LongList> coherences;

    public SearchTerms(
            LongList includes,
            LongList excludes,
            LongList priority,
            List<LongList> coherences
    ) {
        this.includes = includes;
        this.excludes = excludes;
        this.priority = priority;
        this.coherences = coherences;
    }

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

    public LongList includes() {
        return includes;
    }

    public LongList excludes() {
        return excludes;
    }

    public LongList priority() {
        return priority;
    }

    public List<LongList> coherences() {
        return coherences;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SearchTerms) obj;
        return Objects.equals(this.includes, that.includes) &&
                Objects.equals(this.excludes, that.excludes) &&
                Objects.equals(this.priority, that.priority) &&
                Objects.equals(this.coherences, that.coherences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includes, excludes, priority, coherences);
    }

    @Override
    public String toString() {
        return "SearchTerms[" +
                "includes=" + includes + ", " +
                "excludes=" + excludes + ", " +
                "priority=" + priority + ", " +
                "coherences=" + coherences + ']';
    }

}
