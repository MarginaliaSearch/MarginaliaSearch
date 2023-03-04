package nu.marginalia.index.query.filter;

import java.util.function.LongPredicate;

public class QueryFilterStepFromPredicate implements QueryFilterStepIf {
    private final LongPredicate pred;

    public QueryFilterStepFromPredicate(LongPredicate pred) {
        this.pred = pred;
    }

    @Override
    public boolean test(long value) {
        return pred.test(value);
    }

    @Override
    public double cost() {
        return 0;
    }

    @Override
    public String describe() {
        return "[Predicate]";
    }

}
