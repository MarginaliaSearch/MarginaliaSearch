#include "cpphelpers.hpp"
#include <algorithm>
#include <stdio.h>

/* Pair of 64-bit integers. */
/* The struct is packed to ensure that the struct is exactly 16 bytes in size, as we need to pointer
   alias on an array of 8 byte longs.  Since structs guarantee that the first element is at offset 0,
   and __attribute__((packed)) guarantees that the struct is exactly 16 bytes in size, the only reasonable
   implementation is that the struct is laid out as 2 64-bit integers.  This assumption works only as
   long as there are at most 2 fields.

   This is a non-portable low level hack, but all this code strongly assumes a x86-64 Linux environment.
   For other environments (e.g. outside of prod), the Java implementation code will have to do.
*/
struct __attribute__((packed)) p64x2 {
    int64_t a;
    int64_t b;
};

void ms_sort_64(int64_t* area, uint64_t start, uint64_t end) {
  std::sort(&area[start], &area[end]);
}

void ms_sort_128(int64_t* area, uint64_t start, uint64_t end) {
  std::sort(
    reinterpret_cast<p64x2 *>(&area[start]),
    reinterpret_cast<p64x2 *>(&area[end]),
    [](const p64x2& fst, const p64x2& snd) {
    return fst.a < snd.a;
  });
}

inline int64_t encodeSearchMiss64(int64_t value) {
    return -1 - std::max(int64_t(0), value);
}
inline int64_t encodeSearchMiss128(int64_t value) {
    return -2 - std::max(int64_t(0), value);
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
