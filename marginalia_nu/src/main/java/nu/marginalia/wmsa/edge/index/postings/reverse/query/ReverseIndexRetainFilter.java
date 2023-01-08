package nu.marginalia.wmsa.edge.index.postings.reverse.query;

import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.wmsa.edge.index.query.filter.QueryFilterStepIf;

public record ReverseIndexRetainFilter(BTreeReader range) implements QueryFilterStepIf {

    @Override
    public void apply(LongQueryBuffer buffer) {
        range.retainEntries(buffer);
        buffer.finalizeFiltering();
    }

    public boolean test(long id) {
        return range.findEntry(id) >= 0;
    }

    @Override
    public double cost() {
        return range.numEntries();
    }

    @Override
    public String describe() {
        return "ReverseIndexRetainFilter";
    }
}
