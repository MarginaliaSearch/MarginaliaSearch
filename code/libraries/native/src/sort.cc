#include <algorithm>
#include <cstdint>

extern "C" {
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

/* Same as above for 192 bits */
struct __attribute__((packed)) p64x3 {
    int64_t a;
    int64_t b;
    int64_t c;
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

void ms_sort_192(int64_t* area, uint64_t start, uint64_t end) {
  std::sort(
    reinterpret_cast<p64x3 *>(&area[start]),
    reinterpret_cast<p64x3 *>(&area[end]),
    [](const p64x3& fst, const p64x3& snd) {
    return fst.a < snd.a;
  });
}

long count_distinct(int64_t* a, int64_t* b, long aEnd, long bEnd) {
   long aPos = 0;
   long bPos = 0;

   int64_t distinct = 0;
   int64_t lastValue = 0;

   while (aPos < aEnd && bPos < bEnd) {
      long aVal = a[aPos];
      long bVal = b[bPos];

      long val;
      if (aVal < bVal) {
          val = aVal;
          aPos++;
      } else if (bVal < aVal) {
          val = bVal;
          bPos++;
      } else {
          val = aVal;
          aPos++;
          bPos++;
      }

      if (distinct == 0 || (val != lastValue)) {
          distinct++;
      }

      lastValue = val;
    }

    while (aPos < aEnd) {
      int64_t val = a[aPos++];
      if (distinct == 0 || val != lastValue) {
          distinct++;
      }
      lastValue = val;
    }

    while (bPos < bEnd) {
      int64_t val = b[bPos++];
      if (distinct == 0 || val != lastValue) {
          distinct++;
      }
      lastValue = val;
    }

    return distinct;
}

long merge_arrays_1(int64_t* out, int64_t* a, int64_t* b, long aEnd, long bEnd) {
    long aPos = 0;
    long bPos = 0;
    long outPos = 0;

    int64_t lastValue = 0;

    while (aPos < aEnd && bPos < bEnd) {
        int64_t aVal = a[aPos];
        int64_t bVal = b[bPos];

        int64_t setVal;

        if (aVal < bVal) {
            setVal = aVal;

            aPos++;
        } else if (bVal < aVal) {
            setVal = bVal;

            bPos++;
        } else {
            setVal = aVal;

            aPos++;
            bPos++;
        }

        if (setVal != lastValue || outPos == 0) {
            out[outPos++] = setVal;

            lastValue = setVal;
        }
    }

    while (aPos < aEnd) {
        int64_t val = a[aPos++];

        if (val != lastValue || outPos == 0) {
            out[outPos++] = val;
            lastValue = val;
        }
    }

    while (bPos < bEnd) {
        int64_t val = b[bPos++];

        if (val != lastValue || outPos == 0) {
            out[outPos++] = val;

            lastValue = val;
        }
    }

    return outPos;
}


long merge_arrays_2(int64_t* out, int64_t* a, int64_t* b, long aEnd, long bEnd) {
    long aPos = 0;
    long bPos = 0;
    long outPos = 0;

    int64_t lastValue = 0;

    while (aPos < aEnd && bPos < bEnd) {
        int64_t aVal = a[aPos];
        int64_t bVal = b[bPos];

        int64_t setVal;
        int64_t setArg1;

        if (aVal < bVal) {
            setVal = aVal;
            setArg1 = a[aPos+1];

            aPos+=2;
        } else if (bVal < aVal) {
            setVal = bVal;
            setArg1 = b[bPos+1];

            bPos+=2;
        } else {
            setVal = aVal;
            setArg1 = a[aPos+1];

            aPos+=2;
            bPos+=2;
        }

        if (setVal != lastValue || outPos == 0) {
            out[outPos++] = setVal;
            out[outPos++] = setArg1;

            lastValue = setVal;
        }
    }

    while (aPos < aEnd) {
        int64_t val = a[aPos++];
        int64_t arg1 = a[aPos++];

        if (val != lastValue || outPos == 0) {
            out[outPos++] = val;
            out[outPos++] = arg1;
            lastValue = val;
        }
    }

    while (bPos < bEnd) {
        int64_t val = b[bPos++];
        int64_t arg1 = b[bPos++];

        if (val != lastValue || outPos == 0) {
            out[outPos++] = val;
            out[outPos++] = arg1;

            lastValue = val;
        }
    }

    return outPos;
}

long merge_arrays_3(int64_t* out, int64_t* a, int64_t* b, long aEnd, long bEnd) {
    long aPos = 0;
    long bPos = 0;
    long outPos = 0;

    int64_t lastValue = 0;

    while (aPos < aEnd && bPos < bEnd) {
        int64_t aVal = a[aPos];
        int64_t bVal = b[bPos];

        int64_t setVal;
        int64_t setArg1;
        int64_t setArg2;

        if (aVal < bVal) {
            setVal = aVal;
            setArg1 = a[aPos+1];
            setArg2 = a[aPos+2];

            aPos+=3;
        } else if (bVal < aVal) {
            setVal = bVal;
            setArg1 = b[bPos+1];
            setArg2 = b[bPos+2];

            bPos+=3;
        } else {
            setVal = aVal;
            setArg1 = a[aPos+1];
            setArg2 = a[aPos+2];

            aPos+=3;
            bPos+=3;
        }

        if (setVal != lastValue || outPos == 0) {
            out[outPos++] = setVal;
            out[outPos++] = setArg1;
            out[outPos++] = setArg2;

            lastValue = setVal;
        }
    }

    while (aPos < aEnd) {
        int64_t val = a[aPos++];
        int64_t arg1 = a[aPos++];
        int64_t arg2 = a[aPos++];

        if (val != lastValue || outPos == 0) {
            out[outPos++] = val;
            out[outPos++] = arg1;
            out[outPos++] = arg2;
            lastValue = val;
        }
    }

    while (bPos < bEnd) {
        int64_t val = b[bPos++];
        int64_t arg1 = b[bPos++];
        int64_t arg2 = b[bPos++];

        if (val != lastValue || outPos == 0) {
            out[outPos++] = val;
            out[outPos++] = arg1;
            out[outPos++] = arg2;

            lastValue = val;
        }
    }

    return outPos;
}

}