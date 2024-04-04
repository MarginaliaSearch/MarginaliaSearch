package nu.marginalia.api.searchquery.model.compiled.aggregate;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CqExpression;

import java.util.List;
import java.util.function.IntToDoubleFunction;
import java.util.function.ToDoubleFunction;

public class CqDoubleSumOperator implements CqExpression.DoubleVisitor {

    private final IntToDoubleFunction operator;

    public <T> CqDoubleSumOperator(CompiledQuery<T> query, ToDoubleFunction<T> operator) {
        this.operator = idx -> operator.applyAsDouble(query.at(idx));
    }

    @Override
    public double onAnd(List<? extends CqExpression> parts) {
        double value = 0;
        for (var part : parts) {
            value += part.visit(this);
        }
        return value;
    }

    @Override
    public double onOr(List<? extends CqExpression> parts) {
        double value = parts.getFirst().visit(this);
        for (int i = 1; i < parts.size(); i++) {
            value = Math.max(value, parts.get(i).visit(this));
        }
        return value;
    }

    @Override
    public double onLeaf(int idx) {
        return operator.applyAsDouble(idx);
    }
}
