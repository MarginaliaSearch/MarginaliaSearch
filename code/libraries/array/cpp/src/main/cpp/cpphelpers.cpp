#include "cpphelpers.hpp"
#include <algorithm>
#include <stdio.h>

struct p2 {
    int64_t a;
    int64_t b;
};

void ms_sort_64(int64_t* area, uint64_t start, uint64_t end) {
  std::sort(&area[start], &area[end]);
}

void ms_sort_128(int64_t* area, uint64_t start, uint64_t end) {
  struct p2 *startp = (struct p2 *) &area[start];
  struct p2 *endp = (struct p2 *) &area[end];

  // sort based on the first element of the pair
  std::sort(startp, endp, [](struct p2& fst, struct p2& snd) {
    return fst.a < snd.a;
  });
}

int64_t encodeSearchMiss64(int64_t value) {
    return -1 - std::max(int64_t(0), value);
}
int64_t encodeSearchMiss128(int64_t value) {
    return -2 - std::max(int64_t(0), value);
}

int64_t decodeSearchMiss64(long value) {
    return -value - 1;
}
int64_t decodeSearchMiss128(long value) {
    return -value - 2;
}

int64_t ms_linear_search_64(int64_t key, int64_t* area, uint64_t fromIndex, uint64_t toIndex) {
    uint64_t pos = fromIndex;
    for (; pos < toIndex; pos++) {
        int64_t val = area[pos];

        if (val == key) return pos;
        if (val > key) break;
    }

    return encodeSearchMiss64(pos - 1);
}

int64_t ms_linear_search_128(int64_t key, int64_t* area, uint64_t fromIndex, uint64_t toIndex) {
    uint64_t pos = fromIndex;

    for (; pos < toIndex; pos+=2) {
        int64_t val = area[pos];

        if (val == key) return pos;
        if (val > key) break;
    }

    return encodeSearchMiss128(pos - 2);
}

/**         long low = 0;
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

            return encodeSearchMiss(sz, toIndex - sz); */

int64_t ms_binary_search_128(int64_t key, int64_t* area, uint64_t fromIndex, uint64_t toIndex) {
   int64_t low = 0;
   int64_t high = (toIndex - fromIndex) / 2 - 1;

    while (high - low >= 32) {
        int64_t mid = low + (high - low) / 2;
        int64_t midVal = area[fromIndex + mid * 2];

        if (midVal < key) {
            low = mid + 1;
        } else if (midVal > key) {
            high = mid - 1;
        } else {
            return fromIndex + mid * 2;
        }
    }

    for (fromIndex += low * 2; fromIndex < toIndex; fromIndex+=2) {
        int64_t val = area[fromIndex];

        if (val == key) return fromIndex;
        if (val > key) return encodeSearchMiss128(fromIndex);
    }

    return encodeSearchMiss128(toIndex - 2);
}

int64_t ms_binary_search_64upper(int64_t key, int64_t* area, uint64_t fromIndex, uint64_t toIndex) {
    int64_t low = 0;
    int64_t high = toIndex - fromIndex - 1;

    while (high - low > 32) {
        int64_t mid = low + (high - low) / 2;
        int64_t midVal = area[fromIndex + mid];

        if (midVal < key) {
            low = mid + 1;
        } else if (midVal > key) {
            high = mid - 1;
        } else {
            return fromIndex + mid;
        }
    }

    for (fromIndex += low; fromIndex < toIndex; fromIndex++) {
        if (area[fromIndex] >= key) return fromIndex;
    }

    return toIndex;
}
