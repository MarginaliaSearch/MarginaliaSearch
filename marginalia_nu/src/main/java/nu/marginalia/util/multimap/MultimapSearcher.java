package nu.marginalia.util.multimap;

import lombok.experimental.Delegate;

public class MultimapSearcher {
    @Delegate
    private final MultimapFileLong mmf;

    public MultimapSearcher(MultimapFileLong mmf) {
        this.mmf = mmf;
    }

    public boolean binarySearch(long key, long fromIndex, long toIndex) {

        long low = fromIndex;
        long high = toIndex - 1;

        while (low <= high) {
            long mid = (low + high) >>> 1;
            long midVal = get(mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return true; // key found
        }
        return false;  // key not found.
    }

    public long binarySearchUpperBound(long key, long fromIndex, long toIndex) {

        long low = fromIndex;
        long high = toIndex - 1;

        while (low <= high) {
            long mid = (low + high) >>> 1;
            long midVal = get(mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid;
        }
        return low;
    }

    public long binarySearchUpperBound(long key, long fromIndex, long toIndex, long mask) {

        long low = fromIndex;
        long high = toIndex - 1;

        while (low <= high) {
            long mid = (low + high) >>> 1;
            long midVal = get(mid) & mask;

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid;
        }
        return low;
    }

    public long binarySearchUpperBoundNoMiss(long key, long fromIndex, long toIndex) {

        long low = fromIndex;
        long high = toIndex - 1;

        while (low <= high) {
            long mid = (low + high) >>> 1;
            long midVal = get(mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid;
        }
        return -1;
    }


    public long binarySearchUpperBoundNoMiss(long key, long fromIndex, long toIndex, long mask) {

        long low = fromIndex;
        long high = toIndex - 1;

        while (low <= high) {
            long mid = (low + high) >>> 1;
            long midVal = get(mid) & mask;

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid;
        }
        return -1;
    }


    public long binarySearchUpperBoundNoMiss(long key, long fromIndex, long step, long steps, long mask) {

        long low = 0;
        long high = steps - 1;

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
        return -1;
    }
}
