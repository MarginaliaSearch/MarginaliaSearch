package nu.marginalia.index.results.model.ids;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Arrays;
import java.util.stream.LongStream;

public final class TermIdList {
    public final long[] array;

    public TermIdList(long[] array) {
        this.array = array;
    }

    public TermIdList(LongArrayList list) {
        this(list.toLongArray());
    }

    public int size() {
        return array.length;
    }

    public LongStream stream() {
        return LongStream.of(array);
    }

    public long[] array() {
        return array;
    }

    public long at(int i) {
        return array[i];
    }

    public boolean contains(long id) {
        // array is typically small and unsorted, so linear search is fine
        for (int i = 0; i < array.length; i++) {
            if (array[i] == id) {
                return true;
            }
        }
        return false;
    }

    public int indexOf(long id) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == id) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (TermIdList) obj;
        return Arrays.equals(this.array, that.array);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

}
