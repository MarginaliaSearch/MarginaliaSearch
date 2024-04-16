package nu.marginalia.index.query.filter;

import nu.marginalia.array.buffer.LongQueryBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class QueryFilterAnyOf implements QueryFilterStepIf {
    private final List<QueryFilterStepIf> steps;

    public QueryFilterAnyOf(List<? extends QueryFilterStepIf> steps) {
        this.steps = new ArrayList<>(steps.size());

        for (var step : steps) {
            if (step instanceof QueryFilterAnyOf anyOf) {
                this.steps.addAll(anyOf.steps);
            } else {
                this.steps.add(step);
            }
        }
    }

    public QueryFilterAnyOf(QueryFilterStepIf... steps) {
        this(List.of(steps));
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

        if (steps.size() == 1) {
            steps.getFirst().apply(buffer);
            return;
        }

        int start = 0;
        final int endOfValidData = buffer.end; // End of valid data range

        // The filters act as a partitioning function, where anything before buffer.end
        // is "in", and is guaranteed to be sorted; and anything after buffer.end is "out"
        // but no sorting guaranteed is provided.

        // To provide a conditional filter, we re-sort the "out" range, slice it and apply filtering to the slice

        for (var step : steps)
        {
            var slice = buffer.slice(start, endOfValidData);
            slice.data.quickSort(0, slice.size());

            step.apply(slice);
            start += slice.end;
        }

        // After we're done, read and write pointers should be 0 and "end" should be the length of valid data,
        // normally done through buffer.finalizeFiltering(); but that won't work here
        buffer.reset();
        buffer.end = start;

        // After all filters have been applied, we must re-sort all the retained data
        // to uphold the sortedness contract
        buffer.data.quickSort(0, buffer.end);
    }

    public String describe() {
        StringJoiner sj = new StringJoiner(",", "[Any Of: ", "]");
        for (var step : steps) {
            sj.add(step.describe());
        }
        return sj.toString();
    }
}
