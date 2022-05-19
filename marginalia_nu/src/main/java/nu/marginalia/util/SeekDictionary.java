package nu.marginalia.util;

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.function.ToIntFunction;

public abstract class SeekDictionary<T> {
    private final ArrayList<T> banks = new ArrayList<>();
    private final TIntArrayList offsets = new TIntArrayList();

    public static <T> SeekDictionary<T> of(ToIntFunction<T> length) {
        return new SeekDictionary<T>() {
            @Override
            public int length(T obj) {
                return length.applyAsInt(obj);
            }
        };
    }
    public T last() {
        return banks.get(banks.size()-1);
    }
    public int lastStart() {
        return offsets.get(offsets.size()-1);
    }

    public abstract int length(T obj);
    public int end() {
        if (banks.isEmpty()) return 0;

        return (offsets.getQuick(offsets.size()-1) + length(last()));
    }

    public void add(T obj) {

        if (banks.isEmpty()) {
            banks.add(obj);
            offsets.add(0);
        }
        else {
            offsets.add(end());
            banks.add(obj);
        }
    }

    public T bankForOffset(int offset) {
        return banks.get(idxForOffset(offset));
    }

    public int idxForOffset(int offset) {

        int high = offsets.size() - 1;
        int low = 0;

        while ( low <= high ) {
            int mid = ( low + high ) >>> 1;
            int midVal = offsets.getQuick(mid);

            if ( midVal < offset ) {
                low = mid + 1;
            }
            else if ( midVal > offset ) {
                high = mid - 1;
            }
            else {
                return mid;
            }
        }
        return low-1;

    }

}
