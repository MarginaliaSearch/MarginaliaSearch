package nu.marginalia.wmsa.edge.index.reader.query.types;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;

import javax.annotation.Nullable;
import java.util.List;
import java.util.StringJoiner;

public interface QueryFilterStep extends Comparable<QueryFilterStep> {
    @Nullable
    SearchIndex getIndex();

    boolean test(long value);

    double cost();

    default int compareTo(QueryFilterStep other) {
        return (int)(cost() - other.cost());
    }

    String describe();

    /**
     * Move each value in items to the beginning of the array,
     * and return the number of matching items.
     *
     * The remaining values are undefined.
     */
    default int retainDestructive(long[] items, int max) {
        int keep = 0;
        for (int i = 0; i < max; i++) {
            if (test(items[i])) {
                if (i != keep) {
                    items[keep] = items[i];
                }
                keep++;
            }
        }
        return keep;
    }

    /**
     * Move each value in items to the beginning of the array,
     * and return the number of matching items. The values that do
     * not pass the test are moved to the end of the array.
     */
    default int retainReorder(long[] items, int start, int max) {
        int keep = 0;
        for (int i = start; i < max; i++) {
            if (test(items[i])) {
                if (i != keep) {
                    long tmp = items[keep];
                    items[keep] = items[i];
                    items[i] = tmp;
                }
                keep++;
            }
        }
        return keep;
    }


    static QueryFilterStep noPass() {
        return NoPassFilter.instance;
    }
    static QueryFilterStep anyOf(List<? extends QueryFilterStep> steps) {
        return new AnyOfFilter(steps);
    }


}

class AnyOfFilter implements QueryFilterStep {
    private final List<? extends QueryFilterStep> steps;

    AnyOfFilter(List<? extends QueryFilterStep> steps) {
        this.steps = steps;
    }

    public SearchIndex getIndex() { return null; }

    public double cost() {
        return steps.stream().mapToDouble(QueryFilterStep::cost).average().orElse(0.);
    }

    @Override
    public boolean test(long value) {
        for (var step : steps) {
            if (step.test(value))
                return true;
        }
        return false;
    }

    public String describe() {
        StringJoiner sj = new StringJoiner(",", "[Any Of: ", "]");
        for (var step : steps) {
            sj.add(step.describe());
        }
        return sj.toString();
    }
}

class NoPassFilter implements QueryFilterStep {
    static final QueryFilterStep instance = new NoPassFilter();

    @Override
    public boolean test(long value) {
        return false;
    }
    public SearchIndex getIndex() { return null; }
    public double cost() { return 0.; }

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