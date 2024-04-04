package nu.marginalia.index.query.filter;

import nu.marginalia.array.buffer.LongQueryBuffer;

import java.util.List;
import java.util.StringJoiner;

public class QueryFilterAnyOf implements QueryFilterStepIf {
    private final List<? extends QueryFilterStepIf> steps;

    public QueryFilterAnyOf(List<? extends QueryFilterStepIf> steps) {
        this.steps = steps;
    }

    public double cost() {
        return steps.stream().mapToDouble(QueryFilterStepIf::cost).sum();
    }

    @Override
    public boolean test(long value) {
        for (var step : steps) {
            if (step.test(value))
                return true;
        }
        return false;
    }


    public void apply(LongQueryBuffer buffer) {
        if (steps.isEmpty())
            return;

        int start = 0;
        int end = buffer.end;

        for (var step : steps)
        {
            var slice = buffer.slice(start, end);
            slice.data.quickSort(0, slice.size());

            step.apply(slice);
            start += slice.end;
        }

        buffer.data.quickSort(0, start);

        // Special finalization
        buffer.reset();
        buffer.end = start;
    }

    public String describe() {
        StringJoiner sj = new StringJoiner(",", "[Any Of: ", "]");
        for (var step : steps) {
            sj.add(step.describe());
        }
        return sj.toString();
    }
}
