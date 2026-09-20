#include <algorithm>
#include <cstddef>
#include <cstdint>

namespace {
constexpr size_t tile_size = 64;
constexpr uint64_t offset_mask = (uint64_t(1) << 48) - 1;

uint64_t unzigzag(uint64_t value) {
    return (value >> 1) ^ (uint64_t(0) - (value & 1));
}
}

// See 'FullPreindexBlockTransform.java'

extern "C" void ms_encode_full_preindex(const uint64_t* input, uint8_t* output, int records) {
    uint64_t previous_doc = 0, previous_offset = 0;
    uint64_t tile[3][tile_size];

    const size_t count = records;

    for (size_t base = 0; base < count; base += tile_size) {
        const size_t n = std::min(tile_size, count - base);

        for (size_t i = 0; i < n; ++i) {
            const uint64_t doc = input[3 * (base + i)];
            const uint64_t delta = doc - previous_doc;
            tile[0][i] = (delta << 1) ^ (uint64_t(0) - (delta >> 63));
            previous_doc = doc;

            const uint64_t position = input[3 * (base + i) + 1];
            const uint64_t offset = position & offset_mask;
            const uint64_t offset_delta = (offset - previous_offset) & offset_mask;
            const uint64_t encoded_delta = ((offset_delta << 1)
                    ^ (uint64_t(0) - (offset_delta >> 47))) & offset_mask;

            tile[1][i] = (position & ~offset_mask) | encoded_delta;
            previous_offset = offset;
            tile[2][i] = input[3 * (base + i) + 2];
        }

        for (size_t column = 0; column < 3; ++column) {
            for (size_t byte = 0; byte < 8; ++byte) {
                uint8_t* plane = output + (8 * column + byte) * count + base;
                for (size_t i = 0; i < n; ++i)
                    plane[i] = uint8_t(tile[column][i] >> (8 * byte));
            }
        }
    }
}

extern "C" void ms_decode_full_preindex(const uint8_t* input, uint64_t* output, int records) {
    uint64_t previous_doc = 0, previous_offset = 0;
    uint64_t tile[3][tile_size];
    const size_t count = records;

    for (size_t base = 0; base < count; base += tile_size) {
        const size_t n = std::min(tile_size, count - base);

        for (size_t column = 0; column < 3; ++column) {
            const uint8_t* first = input + 8 * column * count + base;

            for (size_t i = 0; i < n; ++i) {
                tile[column][i] = first[i];
            }

            for (size_t byte = 1; byte < 8; ++byte) {
                const uint8_t* plane = first + byte * count;
                for (size_t i = 0; i < n; ++i) {
                    tile[column][i] |= uint64_t(plane[i]) << (8 * byte);
                }
            }
        }

        for (size_t i = 0; i < n; ++i) {
            previous_doc += unzigzag(tile[0][i]);
            output[3 * (base + i)] = previous_doc;

            const uint64_t position = tile[1][i];
            previous_offset = (previous_offset + unzigzag(position & offset_mask)) & offset_mask;

            output[3 * (base + i) + 1] = (position & ~offset_mask) | previous_offset;
            output[3 * (base + i) + 2] = tile[2][i];
        }
    }
}