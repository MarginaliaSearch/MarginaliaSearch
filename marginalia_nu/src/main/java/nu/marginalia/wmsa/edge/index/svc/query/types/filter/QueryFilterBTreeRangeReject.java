package nu.marginalia.wmsa.edge.index.svc.query.types.filter;

import nu.marginalia.util.btree.BTreeQueryBuffer;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexURLRange;

public record QueryFilterBTreeRangeReject(SearchIndexURLRange range) implements QueryFilterStepIf {

    @Override
    public void apply(BTreeQueryBuffer buffer) {
        range.rejectUrls(buffer);
        buffer.finalizeFiltering();
    }

    public boolean test(long id) {
        return !range.hasUrl(id);
    }

    @Override
    public double cost() {
        return range.numEntries();
    }

    @Override
    public String describe() {
        return "Reject: UrlRange[]";
    }
}
