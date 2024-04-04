package nu.marginalia.index.query.filter;

import nu.marginalia.array.buffer.LongQueryBuffer;

import java.util.List;
import java.util.StringJoiner;

public class QueryFilterAllOf implements QueryFilterStepIf {
    private final List<? extends QueryFilterStepIf> steps;

    public QueryFilterAllOf(List<? extends QueryFilterStepIf> steps) {
        this.steps = steps;
    }

    public double cost() {
        double prod = 1.;

        for (var step : steps) {
            double cost = step.cost();
            if (cost > 1.0) {
                prod *= Math.log(cost);
            }
            else {
                prod += cost;
            }
        }

        return prod;
    }

    @Override
    public boolean test(long value) {
        for (var step : steps) {
            if (!step.test(value))
                return false;
        }
        return true;
    }


    public void apply(LongQueryBuffer buffer) {
        if (steps.isEmpty())
            return;

        for (var step : steps) {
            step.apply(buffer);
        }
    }

    public String describe() {
        StringJoiner sj = new StringJoiner(",", "[All Of: ", "]");
        for (var step : steps) {
            sj.add(step.describe());
        }
        return sj.toString();
    }
}
