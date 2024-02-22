package nu.marginalia.index.query;

import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.filter.QueryFilterStepIf;

public record ReverseIndexRejectFilter(BTreeReader range) implements QueryFilterStepIf {

    @Override
    public void apply(LongQueryBuffer buffer) {
        range.rejectEntries(buffer);
        buffer.finalizeFiltering();
    }

    public boolean test(long id) {
        return range.findEntry(id) < 0;
    }

    @Override
    public double cost() {
        return range.numEntries();
    }

    @Override
    public String describe() {
        return "ReverseIndexRejectFilter[]";
    }
}
