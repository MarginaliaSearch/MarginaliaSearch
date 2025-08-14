package nu.marginalia.array.page;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
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
 *
 */
public class LongQueryBuffer {
    /** Direct access to the data in the buffer,
     * guaranteed to be populated until `end` */
    public final LongArray data;

    /** Number of items in the data buffer */
    public int end;

    private int read = 0;
    private int write = 0;

    private LongQueryBuffer(LongArray array, int size) {
        this.data = array;
        this.end = size;
    }

    public LongQueryBuffer(int size) {
        this.data = LongArrayFactory.onHeapConfined(size);
        this.end = 0;
    }

    public LongQueryBuffer(long[] data, int size) {
        this.data = LongArrayFactory.onHeapConfined(size);
        this.data.set(0, data);
        this.end = size;
    }

    public long[] copyData() {
        long[] copy = new long[end];
        data.forEach(0, end, (pos, val) -> copy[(int)pos]=val );
        return copy;
    }

    public long[] copyFilterData() {
        long[] copy = new long[write];
        data.forEach(0, write, (pos, val) -> copy[(int)pos]=val );
        return copy;
    }

    public boolean fitsMore() {
        return end < data.size();
    }

    public int addData(MemorySegment source, long sourceOffset, int nMax) {
        int n = Math.min(nMax, (int) data.size() - end);

        MemorySegment.copy(source, ValueLayout.JAVA_LONG, sourceOffset, data.getMemorySegment(), ValueLayout.JAVA_LONG, 8L * end, n);

        end += n;

        return n;
    }

    /** Dispose of the buffer and release resources */
    public void dispose() {
        data.close();
    }

    public boolean isEmpty() {
        return end == 0;
    }

    public int size() {
        return end;
    }

    public void reset() {
        end = (int) data.size();
        read = 0;
        write = 0;
    }

    public void zero() {
        end = 0;
        read = 0;
        write = 0;
    }

    public LongQueryBuffer slice(int start, int end) {
        return new LongQueryBuffer(data.range(start, end), end - start);
    }

    /* ==  Filtering methods == */

    /** Returns the current value at the read pointer.
     */
    public long currentValue() {
        return data.get(read);
    }

    /** Advances the read pointer and returns true if there are more values to read. */
    public boolean rejectAndAdvance() {
        return ++read < end;
    }

    /** Retains the current value at the read pointer and advances the read and write pointers.
     *  Returns true if there are more values to read.
     *  <p></p> To enable "or" style criterias, the method swaps the current value with the value
     *  at the write pointer, so that it's retained at the end of the buffer.
     */
    public boolean retainAndAdvance() {
        if (read != write) {
            long tmp = data.get(write);
            data.set(write, data.get(read));
            data.set(read, tmp);
        }

        write++;

        return ++read < end;
    }

    /** Retains all values in the buffer, and updates
     * the read and write pointer to the end pointer,
     * as though all values were retained.
     */
    public void retainAll() {
        write = end;
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
        end = write;
        read = 0;
        write = 0;
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

    @SuppressWarnings("preview")
    public ByteBuffer asByteBuffer() {
        return data.getMemorySegment().asByteBuffer();
    }

    public String toString() {
        return getClass().getSimpleName() + "[" +
            "read = " + read +
            ",write = " + write +
            ",end = " + end +
            ",data = [" + Arrays.toString(copyData()) + "]]";
    }


}
