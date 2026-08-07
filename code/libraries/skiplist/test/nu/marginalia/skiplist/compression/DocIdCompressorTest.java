package nu.marginalia.skiplist.compression;

import nu.marginalia.ffi.NativeAlgos;
import nu.marginalia.skiplist.compression.input.ArrayCompressorInput;
import nu.marginalia.skiplist.compression.output.ByteBufferCompressorBuffer;
import nu.marginalia.skiplist.compression.output.SegmentCompressorBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.Random;


class DocIdCompressorTest {
    @Test
    public void testCompressTrivial() {
        MemorySegment seg = Arena.ofAuto().allocate(1024, 1);

        System.out.println(DocIdCompressor.compress(new ArrayCompressorInput(1, 5, 1000, 99999, 1000_000L, 1000_500L), 6, new ByteBufferCompressorBuffer(seg.asByteBuffer())));

        long[] out = new long[6];
        DocIdCompressor.decompress(new SegmentCompressorBuffer(seg, 0), 6, out);
        System.out.println(Arrays.toString(out));
    }

    @Test
    public void testCompress0() {
        MemorySegment seg = Arena.ofAuto().allocate(1024, 1);

        System.out.println(DocIdCompressor.compress(new ArrayCompressorInput(), 0, new ByteBufferCompressorBuffer(seg.asByteBuffer())));

        long[] out = new long[0];
        DocIdCompressor.decompress(new SegmentCompressorBuffer(seg, 0), 0, out);
        System.out.println(Arrays.toString(out));
    }

    /** The native decompressor must agree with the Java implementation, both when it can
     *  use its unguarded fast path (roomy segment) and near the segment boundary (exact slice) */
    @Test
    public void testNativeDecompressParity() {
        Assumptions.assumeTrue(NativeAlgos.isAvailable);

        MemorySegment seg = Arena.ofAuto().allocate(65536, 1);

        for (int iter = 0; iter < 2_000; iter++) {
            Random r = new Random(iter);
            int n = r.nextInt(1, 300);

            long maxDelta = switch (iter % 3) {
                case 0 -> 256;
                case 1 -> 1L << 20;
                default -> Long.MAX_VALUE;
            };

            long[] in = new long[n];
            in[0] = r.nextLong(0, maxDelta);
            for (int i = 1; i < n; i++) {
                in[i] = in[i-1] + r.nextLong(1, maxDelta);
            }

            long size = DocIdCompressor.compress(new ArrayCompressorInput(in), n, new ByteBufferCompressorBuffer(seg.asByteBuffer()));

            long[] javaOut = new long[n];
            SegmentCompressorBuffer javaBuffer = new SegmentCompressorBuffer(seg, 0);
            DocIdCompressor.decompressJava(javaBuffer, n, javaOut);
            Assertions.assertArrayEquals(in, javaOut, "Java mismatch for seed " + iter);

            long[] nativeOut = new long[n];
            long endPos = NativeAlgos.decompressDocIds(seg, 0, n, nativeOut);
            Assertions.assertArrayEquals(in, nativeOut, "Native mismatch for seed " + iter);
            Assertions.assertEquals(javaBuffer.getPos(), endPos, "End position mismatch for seed " + iter);

            long[] boundaryOut = new long[n];
            endPos = NativeAlgos.decompressDocIds(seg.asSlice(0, size), 0, n, boundaryOut);
            Assertions.assertArrayEquals(in, boundaryOut, "Native boundary mismatch for seed " + iter);
            Assertions.assertEquals(size, endPos, "Boundary end position mismatch for seed " + iter);
        }
    }

    /** The fused native decode and match must agree with a straightforward reference
     *  merge, for member and non member keys alike, including near the segment boundary */
    @Test
    public void testNativeDecompressMatchParity() {
        Assumptions.assumeTrue(NativeAlgos.isAvailable);

        MemorySegment seg = Arena.ofAuto().allocate(65536, 1);

        for (int iter = 0; iter < 2_000; iter++) {
            Random r = new Random(iter);
            int n = r.nextInt(1, 300);

            long maxDelta = switch (iter % 3) {
                case 0 -> 4;
                case 1 -> 256;
                default -> 1L << 30;
            };

            long[] values = new long[n];
            values[0] = r.nextLong(1, maxDelta + 1);
            for (int i = 1; i < n; i++) {
                values[i] = values[i-1] + r.nextLong(1, maxDelta + 1);
            }

            long size = DocIdCompressor.compress(new ArrayCompressorInput(values), n, new ByteBufferCompressorBuffer(seg.asByteBuffer()));

            int nKeys = r.nextInt(1, 60);
            long[] keys = new long[nKeys];
            for (int i = 0; i < nKeys; i++) {
                keys[i] = r.nextBoolean() ? values[r.nextInt(n)] : r.nextLong(0, values[n-1] + 10);
            }
            Arrays.sort(keys);

            long valuesOffset = 1000;
            long stride = 8 * 5;

            long[] actual = new long[nKeys];
            long packed = NativeAlgos.decompressMatch(seg.asSlice(0, size), 0, n, keys, 0, valuesOffset, stride, actual, 0);
            int consumed = (int) packed;

            int expectedConsumed = 0;
            long[] expected = new long[nKeys];
            for (long key : keys) {
                if (key > values[n-1]) break;

                int idx = Arrays.binarySearch(values, key);
                expected[expectedConsumed++] = idx >= 0 ? valuesOffset + stride * idx : -1;
            }

            Assertions.assertEquals(expectedConsumed, consumed, "Consumed key count mismatch for seed " + iter);
            for (int i = 0; i < expectedConsumed; i++) {
                Assertions.assertEquals(expected[i], actual[i], "Offset mismatch at " + i + " for seed " + iter);
            }
        }
    }

    /** Decompress from a segment sliced to the exact compressed size, so that the
     *  final values are read close to the segment boundary */
    @Test
    public void testDecompressExactlySizedSegment() {
        MemorySegment seg = Arena.ofAuto().allocate(65536, 1);

        for (int iter = 0; iter < 2_000; iter++) {
            Random r = new Random(iter);
            int n = r.nextInt(1, 100);

            long[] in = new long[n];
            long maxDelta = (iter % 2 == 0) ? 256 : Long.MAX_VALUE;

            in[0] = r.nextLong(0, maxDelta);
            for (int i = 1; i < n; i++) {
                in[i] = in[i-1] + r.nextLong(1, maxDelta);
            }

            long size = DocIdCompressor.compress(new ArrayCompressorInput(in), n, new ByteBufferCompressorBuffer(seg.asByteBuffer()));

            long[] out = new long[n];
            DocIdCompressor.decompress(new SegmentCompressorBuffer(seg.asSlice(0, size), 0), n, out);

            Assertions.assertArrayEquals(in, out, "Mismatch for seed " + iter);
        }
    }

    @Test
    public void testCompressFuzz() {
        MemorySegment seg = Arena.ofAuto().allocate(65536, 1);

        for (int iter = 0; iter < 10_000; iter++) {
            Random r = new Random(iter);
            int n = r.nextInt(5, 500);

            System.out.println("Seed: " + iter + " n = " + n);

            long[] in = new long[n];

            in[0] = r.nextLong(0, Long.MAX_VALUE);
            for (int i = 1; i < n; i++) {
                in[i] = in[i-1] + r.nextLong(0, Long.MAX_VALUE);
            }

            DocIdCompressor.compress(new ArrayCompressorInput(in), in.length, new ByteBufferCompressorBuffer(seg.asByteBuffer()));

            long[] out = new long[n];
            DocIdCompressor.decompress(new SegmentCompressorBuffer(seg, 0), n, out);

            Assertions.assertArrayEquals(in, out);
        }
    }


}