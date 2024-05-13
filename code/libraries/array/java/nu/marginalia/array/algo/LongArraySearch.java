package nu.marginalia.array.algo;

import nu.marginalia.NativeAlgos;
import nu.marginalia.array.buffer.LongQueryBuffer;

public interface LongArraySearch extends LongArrayBase {

    int LINEAR_SEARCH_CUTOFF = 32;

    default long linearSearch(long key, long fromIndex, long toIndex) {
        if (NativeAlgos.isAvailable) {
            return linearSearchNative(key, fromIndex, toIndex);
        } else {
            return linearSearchJava(key, fromIndex, toIndex);
        }
    }

    default long linearSearchN(int sz, long key, long fromIndex, long toIndex) {
        if (NativeAlgos.isAvailable && sz == 2) {
            return linearSearchNative128(key, fromIndex, toIndex);
        } else {
            return linearSearchNJava(sz, key, fromIndex, toIndex);
        }
    }

    default long binarySearchUpperBound(long key, long fromIndex, long toIndex) {
        if (NativeAlgos.isAvailable) {
            return binarySearchNativeUB(key, fromIndex, toIndex);
        } else {
            return binarySearchUpperBoundJava(key, fromIndex, toIndex);
        }
    }

    default long binarySearchN(int sz, long key, long fromIndex, long toIndex) {
        if (NativeAlgos.isAvailable && sz == 2) {
            return binarySearchNative128(key, fromIndex, toIndex);
        } else {
            return binarySearchNJava(sz, key, fromIndex, toIndex);
        }
    }

    default long linearSearchJava(long key, long fromIndex, long toIndex) {
        long pos;

        for (pos = fromIndex; pos < toIndex; pos++) {
            long val = get(pos);

            if (val == key) return pos;
            if (val > key) break;
        }

        return encodeSearchMiss(1, pos - 1);
    }

    default long linearSearchNJava(int sz, long key, long fromIndex, long toIndex) {
        long pos;

        for (pos = fromIndex; pos < toIndex; pos+=sz) {
            long val = get(pos);

            if (val == key) return pos;
            if (val > key) return encodeSearchMiss(sz, pos);
        }

        return encodeSearchMiss(sz, toIndex - sz);
    }

    default long binarySearch(long key, long fromIndex, long toIndex) {
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

    default long binarySearchNJava(int sz, long key, long fromIndex, long toIndex) {
        long low = 0;
        long high = (toIndex - fromIndex)/sz - 1;

        while (high - low >= LINEAR_SEARCH_CUTOFF) {
            long mid = (low + high) >>> 1;
            long midVal = get(fromIndex + sz*mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return fromIndex + sz*mid;
        }

        for (fromIndex += low*sz; fromIndex < toIndex; fromIndex+=sz) {
            long val = get(fromIndex);

            if (val == key) return fromIndex;
            if (val > key) return encodeSearchMiss(sz, fromIndex);
        }

        return encodeSearchMiss(sz, toIndex - sz);
    }


    default long binarySearchUpperBoundJava(long key, long fromIndex, long toIndex) {
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


    default void retain(LongQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        long bv = buffer.currentValue();
        long av = get(searchStart);
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

    default void retainN(LongQueryBuffer buffer, int sz, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        long bv = buffer.currentValue();
        long av = get(searchStart);
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

            pos += sz;

            if (pos < searchEnd) {
                av = get(pos);
            }
            else {
                break;
            }
        }
    }
    default void reject(LongQueryBuffer buffer, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        long bv = buffer.currentValue();
        long av = get(searchStart);
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

    default void rejectN(LongQueryBuffer buffer, int sz, long boundary, long searchStart, long searchEnd) {

        if (searchStart >= searchEnd) return;

        long bv = buffer.currentValue();
        long av = get(searchStart);
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

            pos += sz;
            if (pos < searchEnd) {
                av = get(pos);
            }
            else {
                break;
            }
        }

    }

    static long encodeSearchMiss(int entrySize, long value) {
        return -entrySize - Math.max(0, value);
    }

    static long decodeSearchMiss(int entrySize, long value) {
        return -value - entrySize;
    }
}
