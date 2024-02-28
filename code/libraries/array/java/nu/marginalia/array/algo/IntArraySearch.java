package nu.marginalia.array.algo;

import nu.marginalia.array.buffer.IntQueryBuffer;

public interface IntArraySearch extends IntArrayBase {

    int LINEAR_SEARCH_CUTOFF = 64;

    default long linearSearch(int key, long fromIndex, long toIndex) {
        long pos;

        for (pos = fromIndex; pos < toIndex; pos++) {
            int val = get(pos);

            if (val == key) return pos;
            if (val > key) break;
        }

        return LongArraySearch.encodeSearchMiss(1, pos - 1);
    }

    default long binarySearch(int key, long fromIndex, long toIndex) {
        long low = 0;
        long high = (toIndex - fromIndex) - 1;

        while (high - low >= LINEAR_SEARCH_CUTOFF) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return fromIndex + mid;
        }
        return linearSearch(key, fromIndex + low, fromIndex + high + 1);
    }

    default long binarySearchUpperBound(int key, long fromIndex, long toIndex) {
        long low = 0;
        long high = (toIndex - fromIndex) - 1;

        while (high - low >= LINEAR_SEARCH_CUTOFF) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return fromIndex + mid;
        }

        for (fromIndex += low; fromIndex < toIndex; fromIndex++) {
            if (get(fromIndex) >= key) return fromIndex;
        }

        return toIndex;
    }


    default void retain(IntQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        int bv = buffer.currentValue();
        int av = get(searchStart);
        long pos = searchStart;

        while (bv <= boundary && buffer.hasMore()) {
            if (bv < av) {
                if (!buffer.rejectAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }
            else if (bv == av) {
                if (!buffer.retainAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }

            if (++pos < searchEnd) {
                av = get(pos);
            }
            else {
                break;
            }
        }
    }

    default void reject(IntQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        int bv = buffer.currentValue();
        int av = get(searchStart);
        long pos = searchStart;

        while (bv <= boundary && buffer.hasMore()) {
            if (bv < av) {
                if (!buffer.retainAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }
            else if (bv == av) {
                if (!buffer.rejectAndAdvance()) break;
                bv = buffer.currentValue();
                continue;
            }

            if (++pos < searchEnd) {
                av = get(pos);
            }
            else {
                break;
            }
        }

    }
}
