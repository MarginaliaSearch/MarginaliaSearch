package nu.marginalia.wmsa.edge.index.svc.query.types.filter;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;

import javax.annotation.Nullable;
import java.util.List;

public interface QueryFilterStepIf extends Comparable<QueryFilterStepIf> {
    @Nullable
    SearchIndex getIndex();

    boolean test(long value);

    double cost();

    default int compareTo(QueryFilterStepIf other) {
        return (int)(cost() - other.cost());
    }

    String describe();

    /**
     * Move each value in items to the beginning of the array,
     * and return the number of matching items.
     *
     * The remaining values are undefined.
     */
    default int retainDestructive(long[] items, int max) {
        int keep = 0;
        for (int i = 0; i < max; i++) {
            if (test(items[i])) {
                if (i != keep) {
                    items[keep] = items[i];
                }
                keep++;
            }
        }
        return keep;
    }

    /**
     * Move each value in items to the beginning of the array,
     * and return the number of matching items. The values that do
     * not pass the test are moved to the end of the array.
     */
    default int retainReorder(long[] items, int start, int max) {
        int keep = 0;
        for (int i = start; i < max; i++) {
            if (test(items[i])) {
                if (i != keep) {
                    long tmp = items[keep];
                    items[keep] = items[i];
                    items[i] = tmp;
                }
                keep++;
            }
        }
        return keep;
    }


    static QueryFilterStepIf noPass() {
        return QueryFilterNoPass.instance;
    }
    static QueryFilterStepIf anyOf(List<? extends QueryFilterStepIf> steps) {
        return new QueryFilterAnyOf(steps);
    }


}

