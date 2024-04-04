package nu.marginalia.api.searchquery.model.compiled.aggregate;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.CqExpression;

import java.util.ArrayList;
import java.util.List;

public class CqQueryPathsOperator implements CqExpression.ObjectVisitor<List<LongSet>> {
    private final CompiledQueryLong query;

    public CqQueryPathsOperator(CompiledQueryLong query) {
        this.query = query;
    }

    @Override
    public List<LongSet> onAnd(List<? extends CqExpression> parts) {
        return parts.stream()
                .map(expr -> expr.visit(this))
                .reduce(List.of(), this::combineAnd);
    }

    private List<LongSet> combineAnd(List<LongSet> a, List<LongSet> b) {
        // No-op cases
        if (a.isEmpty())
            return b;
        if (b.isEmpty())
            return a;

        // Simple cases
        if (a.size() == 1) {
            b.forEach(set -> set.addAll(a.getFirst()));
            return b;
        }
        else if (b.size() == 1) {
            a.forEach(set -> set.addAll(b.getFirst()));
            return a;
        }

        // Case where we AND two ORs
        List<LongSet> ret = new ArrayList<>();

        for (var aPart : a) {
            for (var bPart : b) {
                LongSet set = new LongOpenHashSet(aPart.size() + bPart.size());
                set.addAll(aPart);
                set.addAll(bPart);
                ret.add(set);
            }
        }

        return ret;
    }

    @Override
    public List<LongSet> onOr(List<? extends CqExpression> parts) {
        List<LongSet> ret = new ArrayList<>();

        for (var part : parts) {
            ret.addAll(part.visit(this));
        }

        return ret;
    }

    @Override
    public List<LongSet> onLeaf(int idx) {
        var set = new LongArraySet(1);
        set.add(query.at(idx));
        return List.of(set);
    }
}
