package nu.marginalia.index.query;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.skiplist.SkipListReader;

public record ReverseIndexRejectFilter(SkipListReader range) implements QueryFilterStepIf {

    @Override
    public void apply(LongQueryBuffer buffer) {
        range.rejectData(buffer);
        buffer.finalizeFiltering();
    }

    public boolean test(long id) {
        throw new UnsupportedOperationException();
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
