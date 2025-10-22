package nu.marginalia.index.reverse;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.reverse.query.EntrySource;
import nu.marginalia.skiplist.SkipListReader;
import nu.marginalia.skiplist.SkipListValueRanges;

import java.util.Objects;

public class FullIndexEntrySourceWithRangeFilter implements EntrySource {
    private final String name;

    private final String term;
    private final SkipListReader reader;
    private final SkipListValueRanges ranges;
    private int readEntries = 0;

    public FullIndexEntrySourceWithRangeFilter(String name,
                                String term,
                                SkipListReader reader,
                                SkipListValueRanges ranges) {
        this.name = name;
        this.term = term;
        this.reader = reader;
        this.ranges = new SkipListValueRanges(ranges);
    }

    public boolean usesFilter(SkipListValueRanges otherRanges) {
        return Objects.equals(ranges, otherRanges);
    }

    @Override
    public void read(LongQueryBuffer buffer) {
        readEntries += reader.getKeys(buffer, ranges);
    }

    @Override
    public boolean hasMore() {
        return !reader.atEnd();
    }

    @Override
    public String indexName() {
        return name + ":" + term;
    }

    @Override
    public int readEntries() {
        return readEntries;
    }
}
