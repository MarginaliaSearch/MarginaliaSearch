package nu.marginalia.api.searchquery.model.compiled.aggregate;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CqExpression;

import java.util.List;
import java.util.function.IntToLongFunction;
import java.util.function.ToLongFunction;

public class CqLongBitmaskOperator implements CqExpression.LongVisitor {

    private final IntToLongFunction operator;

    public <T> CqLongBitmaskOperator(CompiledQuery<T> query, ToLongFunction<T> operator) {
        this.operator = idx-> operator.applyAsLong(query.at(idx));
    }

    @Override
    public long onAnd(List<? extends CqExpression> parts) {
        long value = ~0L;
        for (var part : parts) {
            value &= part.visit(this);
        }
        return value;
    }

    @Override
    public long onOr(List<? extends CqExpression> parts) {
        long value = 0L;
        for (var part : parts) {
            value |= part.visit(this);
        }
        return value;
    }

    @Override
    public long onLeaf(int idx) {
        return operator.applyAsLong(idx);
    }
}
