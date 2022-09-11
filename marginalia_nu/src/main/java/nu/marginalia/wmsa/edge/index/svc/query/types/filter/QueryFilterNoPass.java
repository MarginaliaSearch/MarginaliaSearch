package nu.marginalia.wmsa.edge.index.svc.query.types.filter;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;

class QueryFilterNoPass implements QueryFilterStepIf {
    static final QueryFilterStepIf instance = new QueryFilterNoPass();

    @Override
    public boolean test(long value) {
        return false;
    }

    public SearchIndex getIndex() {
        return null;
    }

    public double cost() {
        return 0.;
    }

    public int retainDestructive(long[] items, int max) {
        return 0;
    }

    public int retainReorder(long[] items, int start, int max) {
        return 0;
    }

    public String describe() {
        return "[NoPass]";
    }

}
