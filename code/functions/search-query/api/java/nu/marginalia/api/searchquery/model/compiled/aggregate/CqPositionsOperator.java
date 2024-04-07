package nu.marginalia.api.searchquery.model.compiled.aggregate;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CqExpression;

import java.util.List;
import java.util.function.IntToLongFunction;
import java.util.function.ToLongFunction;

public class CqPositionsOperator implements CqExpression.ObjectVisitor<LongSet> {
    private final IntToLongFunction operator;

    public <T> CqPositionsOperator(CompiledQuery<T> query, ToLongFunction<T> operator) {
        this.operator = idx -> operator.applyAsLong(query.at(idx));
    }

    @Override
    public LongSet onAnd(List<? extends CqExpression> parts) {
        LongSet ret = new LongArraySet();

        for (var part : parts) {
            ret = comineSets(ret, part.visit(this));
        }

        return ret;
    }

    private LongSet comineSets(LongSet a, LongSet b) {
        if (a.isEmpty())
            return b;
        if (b.isEmpty())
            return a;

        LongSet ret = newSet(a.size() * b.size());

        var ai = a.longIterator();

        while (ai.hasNext()) {
            long aval = ai.nextLong();

            var bi = b.longIterator();
            while (bi.hasNext()) {
                ret.add(aval & bi.nextLong());
            }
        }

        return ret;
    }

    @Override
    public LongSet onOr(List<? extends CqExpression> parts) {
        LongSet ret = newSet(parts.size());

        for (var part : parts) {
            ret.addAll(part.visit(this));
        }

        return ret;
    }

    @Override
    public LongSet onLeaf(int idx) {
        var set = newSet(1);
        set.add(operator.applyAsLong(idx));
        return set;
    }

    /** Allocate a new set suitable for a collection with the provided cardinality */
    private LongSet newSet(int cardinality) {
        if (cardinality < 8)
            return new LongArraySet(cardinality);
        else
            return new LongOpenHashSet(cardinality);
    }

}
