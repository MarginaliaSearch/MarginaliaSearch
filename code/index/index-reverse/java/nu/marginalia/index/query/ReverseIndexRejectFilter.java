package nu.marginalia.index.query;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.skiplist.SkipListReader;

public record ReverseIndexRejectFilter(SkipListReader range, IndexSearchBudget budget) implements QueryFilterStepIf {

    @Override
    public void apply(LongQueryBuffer buffer) {
        while (budget.hasTimeLeft() && range.tryRejectData(buffer));

        buffer.finalizeFiltering();
    }


    @Override
    public double cost() {
        return 1;
    }

    @Override
    public String describe() {
        return "ReverseIndexRejectFilter[]";
    }
}
