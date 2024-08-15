package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.query.SearchQuery;

import static nu.marginalia.index.model.SearchTermsUtil.getWordId;

public final class SearchTerms {
    private final LongList advice;
    private final LongList excludes;
    private final LongList priority;

    public static final LongArraySet stopWords = new LongArraySet(
            new long[] {
                    getWordId("a"),
                    getWordId("an"),
                    getWordId("the"),
            }
    );

    private final CompiledQueryLong compiledQueryIds;

    public SearchTerms(SearchQuery query,
                       CompiledQueryLong compiledQueryIds)
    {
        this.excludes = new LongArrayList();
        this.priority = new LongArrayList();

        this.advice = new LongArrayList();
        this.compiledQueryIds = compiledQueryIds;

        for (var word : query.searchTermsAdvice) {
            advice.add(getWordId(word));
        }

        for (var word : query.searchTermsExclude) {
            excludes.add(getWordId(word));
        }

        for (var word : query.searchTermsPriority) {
            priority.add(getWordId(word));
        }
    }

    public boolean isEmpty() {
        return compiledQueryIds.isEmpty();
    }

    public long[] sortedDistinctIncludes(LongComparator comparator) {
        LongList list = new LongArrayList(compiledQueryIds.copyData());
        list.sort(comparator);
        return list.toLongArray();
    }


    public LongList excludes() {
        return excludes;
    }
    public LongList advice() {
        return advice;
    }
    public LongList priority() {
        return priority;
    }

    public CompiledQueryLong compiledQuery() { return compiledQueryIds; }

}
