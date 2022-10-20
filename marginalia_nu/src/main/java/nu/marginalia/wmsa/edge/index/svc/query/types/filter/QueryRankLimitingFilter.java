package nu.marginalia.wmsa.edge.index.svc.query.types.filter;

import nu.marginalia.util.btree.BTreeQueryBuffer;

public class QueryRankLimitingFilter implements QueryFilterStepIf
{
    private final int rankLimit;

    public QueryRankLimitingFilter(int rankLimit) {
        this.rankLimit = rankLimit;
    }

    @Override
    public boolean test(long value) {
        long rank = value >>> 32L;
        return rank < rankLimit;
    }

    @Override
    public void apply(BTreeQueryBuffer buffer) {

        while (buffer.hasMore() && test(buffer.currentValue())) {
            buffer.retainAndAdvance();
        }

        buffer.finalizeFiltering();
    }
    @Override
    public double cost() {
        return 0;
    }

    @Override
    public String describe() {
        return getClass().getSimpleName();
    }
}
