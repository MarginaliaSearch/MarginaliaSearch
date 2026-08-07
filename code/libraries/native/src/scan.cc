#include <cstdint>

#if defined(__AVX2__)
#include <immintrin.h>
#endif

// Index of the first element of a sorted ascending array that is at least
// target, or n if there is none.  This is the step both the sequence
// intersection and the minimum distance kernels spend their time in, advancing
// one list to catch up with the largest value seen so far.
//
// Positions are non negative, so comparing against target - 1 with a signed
// greater than cannot wrap.

extern "C" int ms_find_first_ge_scalar(const int32_t* data, int n, int target) {
    for (int i = 0; i < n; i++) {
        if (data[i] >= target) {
            return i;
        }
    }
    return n;
}

extern "C" int ms_find_first_ge(const int32_t* data, int n, int target) {
#if defined(__AVX2__)
    int i = 0;
    const __m256i threshold = _mm256_set1_epi32(target - 1);

    for (; i + 8 <= n; i += 8) {
        // Load 8 values, compare all of them against the threshold, and collapse
        // the per lane results to one bit each
        __m256i values = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(data + i));
        __m256i greater = _mm256_cmpgt_epi32(values, threshold);
        int mask = _mm256_movemask_ps(_mm256_castsi256_ps(greater));
        if (mask != 0) {
            // The lowest set bit is the first matching element
            return i + __builtin_ctz(mask);
        }
    }

    for (; i < n; i++) {
        if (data[i] >= target) {
            return i;
        }
    }
    return n;
#else
    return ms_find_first_ge_scalar(data, n, target);
#endif
}
