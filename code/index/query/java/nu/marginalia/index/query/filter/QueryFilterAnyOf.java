package nu.marginalia.index.query.filter;

import nu.marginalia.array.buffer.LongQueryBuffer;

import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

public class QueryFilterAnyOf implements QueryFilterStepIf {
    private final List<? extends QueryFilterStepIf> steps;

    public QueryFilterAnyOf(List<? extends QueryFilterStepIf> steps) {
        this.steps = steps;
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


    public void apply(LongQueryBuffer buffer) {
        if (steps.isEmpty())
            return;

        int start;
        int end = buffer.end;

        steps.getFirst().apply(buffer);

        // The filter functions will partition the data in the buffer from 0 to END,
        // and update END to the length of the retained items, keeping the retained
        // items sorted but making no guarantees about the rejected half
        //
        // Therefore, we need to re-sort the rejected side, and to satisfy the
        // constraint that the data is sorted up to END, finally sort it again.
        //
        // This sorting may seem like it's slower, but filter.apply(...) is
        // typically much faster than iterating over filter.test(...); so this
        // is more than made up for

        for (int fi = 1; fi < steps.size(); fi++)
        {
            start = buffer.end;
            Arrays.sort(buffer.data, start, end);
            buffer.startFilterForRange(start, end);
            steps.get(fi).apply(buffer);
        }

        Arrays.sort(buffer.data, 0, buffer.end);
    }

    public String describe() {
        StringJoiner sj = new StringJoiner(",", "[Any Of: ", "]");
        for (var step : steps) {
            sj.add(step.describe());
        }
        return sj.toString();
    }
}
