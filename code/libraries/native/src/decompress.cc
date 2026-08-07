#include <cstdint>
#include <cstring>

#if defined(__SSE4_1__)
#include <smmintrin.h>
#define HAVE_SIMD_DECODE 1
#endif

// Decoder for the skip list doc id compression scheme, see DocIdCompressor.java.
// Values are delta coded in groups of ten, each group preceded by a 4 byte control
// word holding ten 3-bit fields with the payload size minus one for each value.

static const uint64_t VALUE_MASKS[9] = {
    0,
    0xFFull,
    0xFFFFull,
    0xFF'FFFFull,
    0xFFFF'FFFFull,
    0xFF'FFFF'FFFFull,
    0xFFFF'FFFF'FFFFull,
    0xFF'FFFF'FFFF'FFFFull,
    ~0ull
};

#ifdef HAVE_SIMD_DECODE

// Shuffle masks keyed by two adjacent 3-bit size fields, expanding a pair of
// packed values into two zero extended 64 bit lanes
struct PairTables {
    alignas(16) uint8_t shuffle[64][16];
    uint8_t length[64];

    PairTables() {
        for (int c = 0; c < 64; c++) {
            int s0 = (c & 7) + 1;
            int s1 = ((c >> 3) & 7) + 1;

            for (int i = 0; i < 8; i++) {
                shuffle[c][i] = i < s0 ? i : 0x80;
                shuffle[c][8 + i] = i < s1 ? s0 + i : 0x80;
            }
            length[c] = s0 + s1;
        }
    }
};

static const PairTables pair_tables;

#endif

// The widest group is 4 + 10*8 bytes, and the pair decoder reads 16 bytes at a time,
// so a full group can be decoded without bounds checks when this many bytes remain
static const int64_t FAST_GROUP_SLACK = 84;

extern "C" {

int64_t ms_decompress_docids(const uint8_t* base, int64_t pos, int64_t limit, int32_t n, int64_t* out) {
    int32_t outIdx = 0;
    uint64_t val = 0;

#ifdef HAVE_SIMD_DECODE
    while (n - outIdx >= 10 && limit - pos >= FAST_GROUP_SLACK) {
        uint32_t control;
        memcpy(&control, base + pos, 4);
        pos += 4;

        for (int pair = 0; pair < 5; pair++) {
            unsigned c = control & 63;
            control >>= 6;

            // Load 16 raw bytes, then shuffle the two packed deltas into zero extended 64 bit lanes
            __m128i raw = _mm_loadu_si128(reinterpret_cast<const __m128i*>(base + pos));
            __m128i deltas = _mm_shuffle_epi8(raw, *reinterpret_cast<const __m128i*>(pair_tables.shuffle[c]));

            // Extract the two lanes and integrate the delta coding
            val += (uint64_t) _mm_cvtsi128_si64(deltas);
            out[outIdx++] = (int64_t) val;
            val += (uint64_t) _mm_extract_epi64(deltas, 1);
            out[outIdx++] = (int64_t) val;

            pos += pair_tables.length[c];
        }
    }
#endif

    while (outIdx < n) {
        uint32_t control;
        memcpy(&control, base + pos, 4);
        pos += 4;

        for (int j = 0; j < 10 && outIdx < n; j++, outIdx++) {
            int size = 1 + (control & 7);
            control >>= 3;

            uint64_t delta;
            if (limit - pos >= 8) {
                memcpy(&delta, base + pos, 8);
                delta &= VALUE_MASKS[size];
            }
            else {
                delta = 0;
                memcpy(&delta, base + pos, size);
            }

            val += delta;
            out[outIdx] = (int64_t) val;
            pos += size;
        }
    }

    return pos;
}

// Decode the compressed run and merge it against sorted keys, writing for each
// consumed key either its value offset or -1 when absent.  Groups of ten values are
// decoded with the pair shuffle tables into a stack buffer, and groups whose max
// falls below the current key are skipped without entering the merge at all, so
// sparse key sets ride along at decode speed.  Decoding stops early when the keys
// are exhausted.  Mirrors SkipListReader.ValueReader's scalar merge.
// Returns the record index in the high 32 bits and the key index in the low.
int64_t ms_decompress_match(const uint8_t* base, int64_t pos, int64_t limit, int32_t n,
                            const int64_t* keys, int32_t nKeys, int32_t keyIdx,
                            int64_t valuesOffset, int64_t offsetStride,
                            int64_t* out, int32_t outIdx)
{
    uint64_t val = 0;
    int32_t recIdx = 0;

    int64_t group[10];

    while (recIdx < n && keyIdx < nKeys) {
        int m = n - recIdx < 10 ? n - recIdx : 10;

#ifdef HAVE_SIMD_DECODE
        if (m == 10 && limit - pos >= FAST_GROUP_SLACK) {
            uint32_t control;
            memcpy(&control, base + pos, 4);
            pos += 4;

            for (int pair = 0; pair < 5; pair++) {
                unsigned c = control & 63;
                control >>= 6;

                // Load 16 raw bytes, then shuffle the two packed deltas into zero extended 64 bit lanes
                __m128i raw = _mm_loadu_si128(reinterpret_cast<const __m128i*>(base + pos));
                __m128i deltas = _mm_shuffle_epi8(raw, *reinterpret_cast<const __m128i*>(pair_tables.shuffle[c]));

                // Extract the two lanes and integrate the delta coding
                val += (uint64_t) _mm_cvtsi128_si64(deltas);
                group[2*pair] = (int64_t) val;
                val += (uint64_t) _mm_extract_epi64(deltas, 1);
                group[2*pair + 1] = (int64_t) val;

                pos += pair_tables.length[c];
            }
        }
        else
#endif
        {
            uint32_t control;
            memcpy(&control, base + pos, 4);
            pos += 4;

            for (int j = 0; j < m; j++) {
                int size = 1 + (control & 7);
                control >>= 3;

                uint64_t delta;
                if (limit - pos >= 8) {
                    memcpy(&delta, base + pos, 8);
                    delta &= VALUE_MASKS[size];
                }
                else {
                    delta = 0;
                    memcpy(&delta, base + pos, size);
                }
                pos += size;

                val += delta;
                group[j] = (int64_t) val;
            }
        }

        if (keys[keyIdx] > group[m-1]) {
            recIdx += m;
            continue;
        }

        int j = 0;
        while (keyIdx < nKeys && j < m) {
            int64_t kv = keys[keyIdx];
            int64_t v = group[j];

            if (kv < v) {
                out[outIdx++] = -1;
                keyIdx++;
            }
            else if (kv == v) {
                out[outIdx++] = valuesOffset + offsetStride * (recIdx + j);
                keyIdx++;
            }
            else {
                j++;
            }
        }

        recIdx += m;
    }

    return ((int64_t) recIdx << 32) | (uint32_t) keyIdx;
}

}
