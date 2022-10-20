package nu.marginalia.wmsa.edge.index.svc.query;

import nu.marginalia.util.btree.BTreeQueryBuffer;
import nu.marginalia.wmsa.edge.index.svc.query.types.EntrySource;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterStepIf;

import java.util.ArrayList;
import java.util.List;

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

    public void getMoreResults(BTreeQueryBuffer dest) {
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

    private boolean fillBuffer(BTreeQueryBuffer dest) {
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
        sb.append("Sources:\n");
        for (var source : sources) {
            sb.append(source).append('\n');
        }
        sb.append("Includes:\n");
        for (var include : inclusionFilter) {
            sb.append("\t").append(include.describe()).append("\n");
        }

        return sb.toString();
    }
}


