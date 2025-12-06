package nu.marginalia.index.reverse.query;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.skiplist.SkipListReader;
import org.apache.logging.log4j.util.Strings;

import java.util.List;

public record ReverseIndexMultiTermRetainFilter(List<SkipListReader> ranges, String name, List<String> terms, IndexSearchBudget budget) implements QueryFilterStepIf {

    @Override
    public void apply(LongQueryBuffer buffer) {
        for (int i = 0; i < ranges.size() && budget.hasTimeLeft() && buffer.hasMore(); i++, buffer.tryOther()) {
            SkipListReader range = ranges.get(i);

            while (range.tryRetainData(buffer) && budget.hasTimeLeft());
        }

        buffer.finalizeMultipass();
    }

    @Override
    public double cost() {
        return 1;
    }

    @Override
    public String describe() {
        return "Retain:" + name + "/" + Strings.join(terms, '|');
    }
}
