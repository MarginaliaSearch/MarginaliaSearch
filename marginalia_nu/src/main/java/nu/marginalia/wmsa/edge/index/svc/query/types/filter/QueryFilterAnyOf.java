package nu.marginalia.wmsa.edge.index.svc.query.types.filter;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;

import java.util.List;
import java.util.StringJoiner;

class QueryFilterAnyOf implements QueryFilterStepIf {
    private final List<? extends QueryFilterStepIf> steps;

    QueryFilterAnyOf(List<? extends QueryFilterStepIf> steps) {
        this.steps = steps;
    }

    public SearchIndex getIndex() {
        return null;
    }

    public double cost() {
        return steps.stream().mapToDouble(QueryFilterStepIf::cost).average().orElse(0.);
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
