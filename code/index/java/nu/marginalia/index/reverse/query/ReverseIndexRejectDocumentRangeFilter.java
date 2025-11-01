package nu.marginalia.index.reverse.query;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.skiplist.SkipListValueRanges;

public record ReverseIndexRejectDocumentRangeFilter(SkipListValueRanges ranges) implements QueryFilterStepIf
{

    @Override
    public void apply(LongQueryBuffer buffer) {
        while (!ranges.atEnd() && buffer.hasMore()) {
            long rangeStart = ranges.start();
            long rangeEnd = ranges.end();
            long cv = Long.MIN_VALUE;

            while (buffer.hasMore() && (cv = buffer.currentValue()) < rangeStart) buffer.retainAndAdvance();
            if (!buffer.hasMore()) break;

            while (buffer.hasMore() && (cv = buffer.currentValue()) < rangeEnd) buffer.rejectAndAdvance();
            if (!buffer.hasMore()) break;

            if (cv >= rangeEnd && !ranges.next()) break;
        }

        // Reject the remaining values, if present
        if (buffer.hasMore()) {
            while (buffer.retainAndAdvance());
        }

        buffer.finalizeFiltering();
    }

    @Override
    public double cost() {
        return 1;
    }

    @Override
    public String describe() {
        return "Reject documentRange";
    }
}
