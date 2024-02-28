package nu.marginalia.array.buffer;

import java.util.Arrays;

public class IntQueryBuffer {
    public final int[] data;
    public int end;

    private int read = 0;
    private int write = 0;

    public IntQueryBuffer(int size) {
        this.data = new int[size];
        this.end = size;
    }

    public IntQueryBuffer(int [] data, int size) {
        this.data = data;
        this.end = size;
    }

    public int[] copyData() {
        return Arrays.copyOf(data, end);
    }

    public boolean isEmpty() {
        return end == 0;
    }

    public int size() {
        return end;
    }

    public int currentValue() {
        return data[read];
    }

    public boolean rejectAndAdvance() {
        return ++read < end;
    }

    public boolean retainAndAdvance() {
        if (read != write) {
            int tmp = data[write];
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

        int prev = currentValue();
        retainAndAdvance();

        while (hasMore()) {

            int val = currentValue();

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
