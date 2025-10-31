package nu.marginalia.skiplist;

import java.util.Arrays;

public class SkipListValueRanges {
    private long[] starts;
    private long[] ends;

    int position = -1;

    public SkipListValueRanges(long[] starts, long[] ends) {
        if (starts.length != ends.length)
            throw new IllegalArgumentException("Mismatching array lengths");

        if (getClass().desiredAssertionStatus()) {
            for (int i = 1; i < starts.length; i++) {
                if (ends[i] <= starts[i]) throw new IllegalArgumentException("Ends before starts");
                if (starts[i] <= starts[i-1]) throw new IllegalArgumentException("Starts not sorted");
                if (ends[i] <= ends[i-1]) throw new IllegalArgumentException("Ends not sorted");
                if (ends[i-1] >= starts[i]) throw new IllegalArgumentException("Arrays are overlapping");
            }
        }

        this.starts = Arrays.copyOf(starts, starts.length);
        this.ends = Arrays.copyOf(ends, ends.length);
    }

    public SkipListValueRanges(SkipListValueRanges other) {
        this(other.starts, other.ends);
    }

    public boolean next() {
        return ++position < starts.length;
    }

    public boolean hasMore() {
        return position + 1 < starts.length;
    }

    public long start() {
        return starts[position];
    }
    public long end() {
        return ends[position];
    }

}
