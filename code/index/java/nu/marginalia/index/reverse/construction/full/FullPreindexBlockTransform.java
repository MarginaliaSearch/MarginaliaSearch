package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.ffi.NativeAlgos;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/** Preprocessing for blocks of (document ID, packed position, metadata) to nearly double compressability.
 */
final class FullPreindexBlockTransform {
    /** {@see nu.marginalia.index.reverse.positions.PositionCodec} */
    private static final long POSITION_OFFSET_MASK = 0x0000_FFFF_FFFF_FFFFL;

    private FullPreindexBlockTransform() { }

    static void encode(MemorySegment source, int records, MemorySegment destination) {
        if (NativeAlgos.isAvailable) {
            NativeAlgos.encodePreindexData(source, records, destination);
        }
        else {
            // Preserve the portable path when the optional native library is unavailable.
            long[] input = source.asSlice(0, 24L * records).toArray(ValueLayout.JAVA_LONG);
            byte[] output = new byte[Math.multiplyExact(24, records)];
            encode(input, records, output);
            MemorySegment.copy(MemorySegment.ofArray(output), 0, destination, 0, output.length);
        }
    }

    static void decode(MemorySegment source, int records, MemorySegment destination) {
        if (NativeAlgos.isAvailable) {
            NativeAlgos.decodePreindexData(source, records, destination);
        }
        else {
            byte[] input = source.asSlice(0, 24L * records).toArray(ValueLayout.JAVA_BYTE);
            long[] output = new long[Math.multiplyExact(3, records)];
            decode(input, records, output);
            MemorySegment.copy(MemorySegment.ofArray(output), 0, destination, 0, 24L * records);
        }
    }

    static void encode(long[] source, int records, byte[] destination) {
        checkLengths(source, records, destination);

        // Process one long column at a time to reduce simultaneous plane accesses.
        long previousDoc = 0;
        for (int i = 0; i < records; i++) {
            long doc = source[3 * i];
            put(destination, i, records, zigzag(doc - previousDoc));
            previousDoc = doc;
        }

        long previousOffset = 0;
        for (int i = 0; i < records; i++) {
            long position = source[3 * i + 1];
            long offset = position & POSITION_OFFSET_MASK;

            // Sign-extend the difference modulo 2^48 before ZigZag encoding it.
            // This preserves arbitrary backward jumps and every offset bit.
            long offsetDelta = ((offset - previousOffset) << 16) >> 16;

            put(destination, 8 * records + i, records, (position & ~POSITION_OFFSET_MASK) | zigzag(offsetDelta));
            previousOffset = offset;
        }

        for (int i = 0; i < records; i++) {
            put(destination, 16 * records + i, records, source[3 * i + 2]);
        }
    }

    static void decode(byte[] source, int records, long[] destination) {
        checkLengths(destination, records, source);

        long previousDoc = 0;
        for (int i = 0; i < records; i++) {
            long doc = previousDoc + unzigzag(get(source, i, records));
            destination[3 * i] = doc;
            previousDoc = doc;
        }

        long previousOffset = 0;
        for (int i = 0; i < records; i++) {
            long position = get(source, 8 * records + i, records);
            long offset = (previousOffset + unzigzag(position & POSITION_OFFSET_MASK)) & POSITION_OFFSET_MASK;
            destination[3 * i + 1] = (position & ~POSITION_OFFSET_MASK) | offset;
            previousOffset = offset;
        }

        for (int i = 0; i < records; i++) {
            destination[3 * i + 2] = get(source, 16 * records + i, records);
        }
    }

    private static void checkLengths(long[] longs, int records, byte[] bytes) {
        Objects.checkFromIndexSize(0, Math.multiplyExact(records, 3), longs.length);
        Objects.checkFromIndexSize(0, Math.multiplyExact(records, 24), bytes.length);
    }

    private static long zigzag(long value) { return (value << 1) ^ (value >> 63); }
    private static long unzigzag(long value) { return (value >>> 1) ^ -(value & 1); }

    // Explicit byte planes avoid per-record temporary buffers and allocation.
    private static void put(byte[] output, int at, int stride, long value) {
        output[at] = (byte) value;
        output[at + stride] = (byte) (value >>> 8);
        output[at + 2 * stride] = (byte) (value >>> 16);
        output[at + 3 * stride] = (byte) (value >>> 24);
        output[at + 4 * stride] = (byte) (value >>> 32);
        output[at + 5 * stride] = (byte) (value >>> 40);
        output[at + 6 * stride] = (byte) (value >>> 48);
        output[at + 7 * stride] = (byte) (value >>> 56);
    }

    private static long get(byte[] input, int at, int stride) {
        return (input[at] & 0xFFL)
                | (input[at + stride] & 0xFFL) << 8
                | (input[at + 2 * stride] & 0xFFL) << 16
                | (input[at + 3 * stride] & 0xFFL) << 24
                | (input[at + 4 * stride] & 0xFFL) << 32
                | (input[at + 5 * stride] & 0xFFL) << 40
                | (input[at + 6 * stride] & 0xFFL) << 48
                | (input[at + 7 * stride] & 0xFFL) << 56;
    }
}
