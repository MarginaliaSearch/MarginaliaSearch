package nu.marginalia.api.searchquery.model.compiled.aggregate;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.CqExpression;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

public class CqBooleanAggregate implements CqExpression.BoolVisitor {

    private final IntPredicate predicate;

    public <T> CqBooleanAggregate(CompiledQuery<T> query, Predicate<T> objPred) {
        this.predicate = idx -> objPred.test(query.at(idx));
    }

    public CqBooleanAggregate(CompiledQueryLong query, LongPredicate longPredicate) {
        this.predicate = idx -> longPredicate.test(query.at(idx));
    }

    @Override
    public boolean onAnd(List<? extends CqExpression> parts) {
        for (var part : parts) {
            if (!part.visit(this)) // short-circuit
                return false;
        }
        return true;
    }

    @Override
    public boolean onOr(List<? extends CqExpression> parts) {
        for (var part : parts) {
            if (part.visit(this)) // short-circuit
                return true;
        }
        return false;
    }

    @Override
    public boolean onLeaf(int idx) {
        return predicate.test(idx);
    }
}
