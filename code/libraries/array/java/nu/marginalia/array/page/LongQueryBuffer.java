package nu.marginalia.array.page;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

/** A buffer for long values that can be used to filter and manipulate the data.
 * It is central to the query processing in the index service.
 * <p></p>
 * The class contains both a read pointer, write pointer, and a buffer end pointer.
 * <p></p>
 * The read and write pointers are used for filtering the data in the buffer, and
 * the end pointer is used to keep track of the length of the data in the buffer.
 * <p></p>
 * Filtering is done via the methods {@link #rejectAndAdvance()}, {@link #retainAndAdvance()},
 * and {@link #finalizeFiltering()}.
 */
public class LongQueryBuffer {
    /** Direct access to the data in the buffer,
     * guaranteed to be populated until `end` */
    public final long[] data;

    /** Number of items in the data buffer */
    public int end;

    private final long[] retained;

    private int read = 0;
    private int write = 0;
    private int rejected = 0;

    public LongQueryBuffer(int size) {
        this.data = new long[size];
        this.retained = new long[size];
        this.end = 0;
    }

    public LongQueryBuffer(long[] data, int size) {
        this.data = data;
        this.retained = new long[data.length];
        this.end = size;
    }

    public long[] copyData() {
        return Arrays.copyOf(data, end);
    }

    public long[] copyFilterData() {
        return Arrays.copyOf(retained, write);
    }

    public boolean fitsMore() {
        return end < data.length;
    }

    public int addData(MemorySegment source, long sourceOffset, int nMax) {
        int n = Math.min(nMax, data.length - end);

        MemorySegment.copy(source, ValueLayout.JAVA_LONG, sourceOffset, data, end, n);

        end += n;

        return n;
    }

    public int addData(long[] newData, int start, int nMax) {
        int n = Math.min(nMax, data.length - end);

        System.arraycopy(newData, start, data, end, n);

        end += n;

        return n;
    }

    public boolean isEmpty() {
        return end == 0;
    }

    public int size() {
        return end;
    }

    public void reset() {
        end = data.length;
        resetFiltering();
    }

    public void zero() {
        end = 0;
        resetFiltering();
    }

    private void resetFiltering() {
        read = 0;
        write = 0;
        rejected = 0;
    }

    /* ==  Filtering methods == */

    /** Returns the current value at the read pointer.
     */
    public long currentValue() {
        return data[read];
    }

    /** Rejects the current value and advances the read pointer, returning true if there are more
     *  values to read.  Rejected values are compacted in order at the front of the data,
     *  so that they can be evaluated against another filter.
     */
    public boolean rejectAndAdvance() {
        assert read < end;
        assert rejected <= read;

        data[rejected++] = data[read];

        return ++read < end;
    }

    public boolean isAscending() {
        for (int i = read + 1; i < end; i++) {
            if (data[i-1] > data[i])
                return false;
        }
        return true;
    }

    /** Retains the current value at the read pointer and advances the read and write pointers.
     *  Returns true if there are more values to read.
     */
    public boolean retainAndAdvance() {
        assert read < end;
        assert write < retained.length;

        retained[write++] = data[read];

        return ++read < end;
    }

    /** Retains all values in the buffer, and updates
     * the read and write pointer to the end pointer,
     * as though all values were retained.
     */
    public void retainAll() {
        System.arraycopy(data, read, retained, write, end - read);
        write += end - read;
        read = end;
    }

    public boolean hasMore() {
        return read < end;
    }

    public boolean hasRetainedData() {
        return write > 0;
    }

    /** Finalizes the filtering by setting the end pointer to the write pointer,
     * and resetting the read and write pointers to zero.
     * <p></p>
     * At this point the buffer can either be read, or additional filtering can be applied.
     */
    public void finalizeFiltering() {
        System.arraycopy(retained, 0, data, 0, write);

        end = write;
        resetFiltering();
    }

    /** Finalizes a multipass filter.  Each pass retains its values in order, but later
     * passes retain values that sort before earlier ones, so the retained values are
     * sorted to keep the buffer ascending for subsequent filtering.
     * <p></p>
     * At this point the buffer can either be read, or additional filtering can be applied.
     */
    public void finalizeMultipass() {
        Arrays.sort(retained, 0, write);
        System.arraycopy(retained, 0, data, 0, write);

        end = write;
        resetFiltering();
    }

    /** Resets the buffer so that the rejected values can be re-evaluated with another filter */
    public void tryOther() {
        end = rejected;
        read = 0;
        rejected = 0;
    }

    /**  Retain only unique values in the buffer, and update the end pointer to the new length.
     * <p></p>
     *   The buffer is assumed to be sorted up until the end pointer.
     */
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
            ",data = [" + Arrays.toString(copyData()) + "]]";
    }
}
