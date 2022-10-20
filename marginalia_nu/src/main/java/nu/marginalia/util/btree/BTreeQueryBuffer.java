package nu.marginalia.util.btree;

import java.util.Arrays;

public class BTreeQueryBuffer {
    public final long[] data;
    public int end;

    private int read = 0;
    private int write = 0;

    public BTreeQueryBuffer(int size) {
        this.data = new long[size];
        this.end = size;
    }

    public BTreeQueryBuffer(long [] data, int size) {
        this.data = data;
        this.end = size;
    }

    private BTreeQueryBuffer(long [] data) {
        this.data = data;
        this.end = data.length;
    }

    public BTreeQueryBuffer[] split(int... splitPoints) {
        BTreeQueryBuffer[] ret = new BTreeQueryBuffer[splitPoints.length+1];

        ret[0] = new BTreeQueryBuffer(Arrays.copyOfRange(data, 0, splitPoints[0]));
        for (int i = 1; i < splitPoints.length; i++) {
            ret[i] = new BTreeQueryBuffer(Arrays.copyOfRange(data, splitPoints[i-1], splitPoints[i]));
        }
        ret[ret.length-1] = new BTreeQueryBuffer(Arrays.copyOfRange(data, splitPoints[splitPoints.length-1], end));

        return ret;
    }

    public void gather(BTreeQueryBuffer... buffers) {
        int start = 0;

        for (var buffer : buffers) {
            System.arraycopy(buffer.data, 0, data, start, buffer.end);
            start += buffer.end;
        }

        this.read = 0;
        this.write = 0;
        this.end = start;
    }

    public long[] copyData() {
        return Arrays.copyOf(data, end);
    }

    public void retainAll() {
        read = write = end;
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
