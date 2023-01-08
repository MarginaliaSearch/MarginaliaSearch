package nu.marginalia.util.array.buffer;

import java.util.Arrays;

public class LongQueryBuffer {
    public final long[] data;
    public int end;

    private int read = 0;
    private int write = 0;

    public LongQueryBuffer(int size) {
        this.data = new long[size];
        this.end = size;
    }

    public LongQueryBuffer(long [] data, int size) {
        this.data = data;
        this.end = size;
    }

    public boolean hasRetainedData() {
        return write > 0;
    }

    public long[] copyData() {
        return Arrays.copyOf(data, end);
    }

    public boolean isEmpty() {
        return end == 0;
    }

    public int size() {
        return end;
    }

    public long currentValue() {
        return data[read];
    }

    public boolean rejectAndAdvance() {
        return ++read < end;
    }

    public boolean retainAndAdvance() {
        if (read != write) {
            long tmp = data[write];
            data[write] = data[read];
            data[read] = tmp;
        }

        write++;

        return ++read < end;
    }

    public boolean hasMore() {
        return read < end;
    }

    public void finalizeFiltering() {
        end = write;
        read = 0;
        write = 0;
    }

    public void startFilterForRange(int pos, int end) {
        read = write = pos;
        this.end = end;
    }

    public void reset() {
        end = data.length;
        read = 0;
        write = 0;
    }

    public void zero() {
        end = 0;
        read = 0;
        write = 0;
        Arrays.fill(data, 0);
    }

    public void uniq() {
        if (end <= 1) return;

        long prev = currentValue();
        retainAndAdvance();

        while (hasMore()) {

            long val = currentValue();

            if (prev == val) {
                rejectAndAdvance();
            } else {
                retainAndAdvance();
                prev = val;
            }

        }

        finalizeFiltering();
    }

    public String toString() {
        return getClass().getSimpleName() + "[" +
            "read = " + read +
            ",write = " + write +
            ",end = " + end +
            ",data = [" + Arrays.toString(Arrays.copyOf(data, end)) + "]]";
    }

}
