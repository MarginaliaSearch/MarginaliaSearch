package nu.marginalia.wmsa.edge.index.query.filter;

import java.util.function.LongPredicate;

public class QueryFilterStepExcludeFromPredicate implements QueryFilterStepIf {
    private final LongPredicate pred;

    public QueryFilterStepExcludeFromPredicate(LongPredicate pred) {
        this.pred = pred;
    }

    @Override
    public boolean test(long value) {
        return !pred.test(value);
    }

    @Override
    public double cost() {
        return 0;
    }

    @Override
    public String describe() {
        return "[!Predicate]";
    }

}
