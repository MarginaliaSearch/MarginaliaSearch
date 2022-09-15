package nu.marginalia.wmsa.edge.index.svc.query;

import nu.marginalia.wmsa.edge.index.svc.query.types.EntrySource;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterStepIf;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;

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

    public int getMoreResults(long[] dest, IndexSearchBudget budget) {
        final EntrySource source = sources.get(si);

        int bufferUtilizedLength = source.read(dest, dest.length);

        if (bufferUtilizedLength <= 0) {
            si++;
            return 0;
        }

        dataCost += bufferUtilizedLength;

        for (var filter : inclusionFilter) {
            bufferUtilizedLength = filter.retainDestructive(dest, bufferUtilizedLength);

            dataCost += bufferUtilizedLength;

            if (bufferUtilizedLength <= 0) {
                si++;
                return 0;
            }
        }

        int count = min(bufferUtilizedLength, dest.length);
        System.arraycopy(dest, 0, dest, 0, count);

        return count;
    }

    public long dataCost() {
        return dataCost;
    }
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Sources:\n");

        for (var source: sources) {
            sb.append("\t").append(source.getIndex().name).append("\n");
        }
        sb.append("Includes:\n");
        for (var include : inclusionFilter) {
            sb.append("\t").append(include.describe()).append("\n");
        }

        return sb.toString();
    }
}


