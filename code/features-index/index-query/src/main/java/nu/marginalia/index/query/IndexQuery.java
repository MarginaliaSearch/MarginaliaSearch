package nu.marginalia.index.query;

import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.array.buffer.LongQueryBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IndexQuery {
    private final List<EntrySource> sources;
    private final List<QueryFilterStepIf> inclusionFilter = new ArrayList<>(10);

    public IndexQuery(List<EntrySource> sources) {
        this.sources = sources;
    }

    public void addInclusionFilter(QueryFilterStepIf filter) {
        inclusionFilter.add(filter);
    }

    private int si = 0;
    private int dataCost;

    public boolean hasMore() {
        return si < sources.size();
    }

    public void getMoreResults(LongQueryBuffer dest) {
        if (!fillBuffer(dest))
            return;

        for (var filter : inclusionFilter) {
            filter.apply(dest);

            dataCost += dest.size();

            if (dest.isEmpty()) {
                return;
            }
        }
    }

    private boolean fillBuffer(LongQueryBuffer dest) {
        for (;;) {
            dest.reset();

            EntrySource source = sources.get(si);
            source.read(dest);

            if (!dest.isEmpty()) {
                break;
            }

            if (!source.hasMore() && ++si >= sources.size())
                return false;
        }

        dataCost += dest.size();

        return !dest.isEmpty();
    }

    public long dataCost() {
        return dataCost;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(sources.stream().map(EntrySource::indexName).collect(Collectors.joining(", ", "[", "]")));
        sb.append(" -> ");
        sb.append(inclusionFilter.stream().map(QueryFilterStepIf::describe).collect(Collectors.joining(", ", "[", "]")));

        return sb.toString();
    }
}


