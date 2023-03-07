package nu.marginalia.index.query.filter;

import nu.marginalia.array.buffer.LongQueryBuffer;

import java.util.List;

public interface QueryFilterStepIf extends Comparable<QueryFilterStepIf> {
    boolean test(long value);

    double cost();

    default int compareTo(QueryFilterStepIf other) {
        return (int)(cost() - other.cost());
    }

    String describe();

    /** <p>For each item in buffer from READ to END, retain the items that
     *  satisfy the filter, maintaining their order, and update END
     *  to the length of the retained items.</p>
     *
     *  <p>Items that are rejected are moved past the new END, all items
     *  are kept, but their order is not guaranteed.</p>
     *
     * <p>ASSUMPTION: buffer is sorted up until end.</p>
     */
    default void apply(LongQueryBuffer buffer) {
        while (buffer.hasMore()) {
            if (test(buffer.currentValue())) {
                buffer.retainAndAdvance();
            }
            else {
                buffer.rejectAndAdvance();
            }
        }
        buffer.finalizeFiltering();
    }

    static QueryFilterStepIf noPass() {
        return QueryFilterNoPass.instance;
    }
    static QueryFilterStepIf anyOf(List<? extends QueryFilterStepIf> steps) {
        return new QueryFilterAnyOf(steps);
    }


}

