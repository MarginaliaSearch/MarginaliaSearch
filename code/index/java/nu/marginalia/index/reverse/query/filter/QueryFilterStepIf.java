package nu.marginalia.index.reverse.query.filter;

import nu.marginalia.array.page.LongQueryBuffer;

public interface QueryFilterStepIf extends Comparable<QueryFilterStepIf> {
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
    void apply(LongQueryBuffer buffer);

}

