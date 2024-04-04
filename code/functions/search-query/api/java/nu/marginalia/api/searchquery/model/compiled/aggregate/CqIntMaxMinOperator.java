package nu.marginalia.api.searchquery.model.compiled.aggregate;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CqExpression;

import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

public class CqIntMaxMinOperator implements CqExpression.IntVisitor {

    private final IntUnaryOperator operator;


    public <T> CqIntMaxMinOperator(CompiledQuery<T> query, ToIntFunction<T> operator) {
        this.operator = idx -> operator.applyAsInt(query.at(idx));
    }

    @Override
    public int onAnd(List<? extends CqExpression> parts) {
        int value = parts.getFirst().visit(this);
        for (int i = 1; i < parts.size(); i++) {
            value = Math.min(value, parts.get(i).visit(this));
        }
        return value;
    }

    @Override
    public int onOr(List<? extends CqExpression> parts) {
        int value = parts.getFirst().visit(this);
        for (int i = 1; i < parts.size(); i++) {
            value = Math.max(value, parts.get(i).visit(this));
        }
        return value;
    }

    @Override
    public int onLeaf(int idx) {
        return operator.applyAsInt(idx);
    }
}
