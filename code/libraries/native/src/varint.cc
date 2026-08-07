#include <cstdint>

// Decoder for the varint coded position sequences, see VarintCodedSequence.java.
// A sequence is a value count followed by position deltas, all encoded as most
// significant group first varints with a continuation bit on every byte but the
// last, at most four bytes per value.

static inline uint32_t decode_one(const uint8_t*& p) {
    uint32_t b = *p++;
    if (!(b & 0x80)) {
        return b;
    }

    uint32_t v = b & 0x7F;
    do {
        b = *p++;
        v = (v << 7) | (b & 0x7F);
    } while (b & 0x80);

    return v;
}

extern "C" {

// Decode a batch of position sequences located at addrs[i] with byte lengths
// lens[i], writing all values sequentially into out and each sequence's value
// count into counts.  Returns the total number of values written.  The caller
// guarantees out has room for the worst case of one value per input byte.
int64_t ms_decode_varint_batch(const int64_t* addrs, const int32_t* lens, int32_t n,
                               int32_t* out, int32_t* counts)
{
    int64_t outIdx = 0;

    for (int32_t s = 0; s < n; s++) {
        const uint8_t* p = reinterpret_cast<const uint8_t*>(addrs[s]);
        const uint8_t* end = p + lens[s];

        if (p >= end) {
            counts[s] = 0;
            continue;
        }

        decode_one(p);   // leading value count, implied by the data that follows

        uint32_t val = 0;
        int32_t written = 0;

        while (p < end) {
            val += decode_one(p);
            out[outIdx + written++] = (int32_t) val;
        }

        counts[s] = written;
        outIdx += written;
    }

    return outIdx;
}

}
