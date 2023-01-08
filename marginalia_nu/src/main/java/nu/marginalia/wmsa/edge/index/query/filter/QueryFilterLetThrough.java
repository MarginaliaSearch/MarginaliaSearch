package nu.marginalia.wmsa.edge.index.query.filter;

public class QueryFilterLetThrough implements QueryFilterStepIf {
    static final QueryFilterStepIf instance = new QueryFilterLetThrough();

    @Override
    public boolean test(long value) {
        return true;
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
