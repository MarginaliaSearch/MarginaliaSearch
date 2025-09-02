package nu.marginalia.index.reverse.query.filter;

import nu.marginalia.array.page.LongQueryBuffer;

public class QueryFilterNoPass implements QueryFilterStepIf {
    static final QueryFilterStepIf instance = new QueryFilterNoPass();

    public void apply(LongQueryBuffer buffer) {
        buffer.finalizeFiltering();
    }

    public double cost() {
        return 1.;
    }

    public String describe() {
        return "[NoPass]";
    }

}
