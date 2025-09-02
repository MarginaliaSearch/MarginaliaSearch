package nu.marginalia.index.reverse.query;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.skiplist.SkipListReader;

public record ReverseIndexRetainFilter(SkipListReader range, String name, long wordId, IndexSearchBudget budget) implements QueryFilterStepIf {

    @Override
    public void apply(LongQueryBuffer buffer) {
        while (budget.hasTimeLeft() && range.tryRetainData(buffer));

        buffer.finalizeFiltering();
    }

    @Override
    public double cost() {
        return 1;
    }

    @Override
    public String describe() {
        return "Retain:" + name + "/" + wordId;
    }
}
