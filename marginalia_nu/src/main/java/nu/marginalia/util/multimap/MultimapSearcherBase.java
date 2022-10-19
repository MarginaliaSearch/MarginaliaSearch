package nu.marginalia.util.multimap;

import lombok.experimental.Delegate;

public class MultimapSearcherBase {
    @Delegate
    private final MultimapFileLongSlice mmf;

    public MultimapSearcherBase(MultimapFileLongSlice mmf) {
        this.mmf = mmf;
    }

    public boolean binarySearchTest(long key, long fromIndex, long n) {

        long low = 0;
        long high = n - 1;

        while (low <= high) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return true;
        }
        return false;
    }

    public long binarySearchLower(long key, long fromIndex, long n) {
        return mmf.binarySearchUpperInternal(key, fromIndex, n);
    }


    public long binarySearchLower(long key, long fromIndex, long n, long mask) {
        long low = 0;
        long high = n - 1;

        while (low <= high) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + mid) & mask;

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return fromIndex + mid;
        }
        return fromIndex + low;
    }


    public long binarySearchLower(long key, long fromIndex, int step, long n, long mask) {
        long low = 0;
        long high = n - 1;

        while (low <= high) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + mid*step) & mask;

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return fromIndex + mid*step;
        }
        return fromIndex + low*step;
    }

    public long binarySearch(long key, long fromIndex, long n) {
        return mmf.binarySearchInternal(key, fromIndex, n);
    }


    public long binarySearch(long key, long fromIndex, long n, long mask) {
        return mmf.binarySearchInternal(key, fromIndex, n, mask);
    }

    public long binarySearch(long key, long fromIndex, int step, long n, long mask) {
        return mmf.binarySearchInternal(key, fromIndex, step, n, mask);
    }
}
