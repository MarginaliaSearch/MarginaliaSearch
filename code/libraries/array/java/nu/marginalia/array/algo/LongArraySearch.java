package nu.marginalia.array.algo;

import nu.marginalia.array.page.LongQueryBuffer;

public interface LongArraySearch extends LongArrayBase {

    default long binarySearch(long key, long fromIndex, long toIndex) {
        long low = 0;
        long high = (toIndex - fromIndex) - 1;
        long len = high - low;

        while (len > 0) {
            var half = len / 2;
            if (get(fromIndex + low + half) < key) {
                low += len - half;
            }
            len = half;
        }

        return fromIndex + low;
    }

    default long binarySearchN(int sz, long key, long fromIndex, long toIndex) {
        long low = 0;
        long high = (toIndex - fromIndex)/sz - 1;
        long len = high - low;

        while (len > 0) {
            var half = len / 2;
            if (get(fromIndex + sz * (low + half)) < key) {
                low += len - half;
            }
            len = half;
        }

        return fromIndex + sz * low;
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


}
