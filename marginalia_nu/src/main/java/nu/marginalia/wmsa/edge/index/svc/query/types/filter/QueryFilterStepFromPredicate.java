package nu.marginalia.wmsa.edge.index.svc.query.types.filter;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongPredicate;

public class QueryFilterStepFromPredicate implements QueryFilterStepIf {
    private final LongPredicate pred;

    public QueryFilterStepFromPredicate(LongPredicate pred) {
        this.pred = pred;
    }

    @Nullable
    @Override
    public SearchIndex getIndex() {
        return null;
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
