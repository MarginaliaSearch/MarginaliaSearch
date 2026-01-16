package nu.marginalia.skiplist.compression;

import nu.marginalia.skiplist.compression.input.ArrayCompressorInput;
import nu.marginalia.skiplist.compression.output.CompressorBuffer;
import nu.marginalia.skiplist.compression.output.SegmentCompressorBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;


class DocIdCompressorTest {
    @Test
    public void testCompressTrivial() {
        MemorySegment seg = Arena.ofAuto().allocate(1024, 1);

        System.out.println(DocIdCompressor.compress(new ArrayCompressorInput(1, 5, 1000, 99999, 1000_000L, 1000_500L), 6, new CompressorBuffer(seg.asByteBuffer())));

        long[] out = new long[6];
        DocIdCompressor.decompress(new SegmentCompressorBuffer(seg, 0), 6, out);
        System.out.println(Arrays.toString(out));
    }

    @Test
    public void testCompress0() {
        MemorySegment seg = Arena.ofAuto().allocate(1024, 1);

        System.out.println(DocIdCompressor.compress(new ArrayCompressorInput(), 0, new CompressorBuffer(seg.asByteBuffer())));

        long[] out = new long[0];
        DocIdCompressor.decompress(new SegmentCompressorBuffer(seg, 0), 0, out);
        System.out.println(Arrays.toString(out));
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

            DocIdCompressor.compress(new ArrayCompressorInput(in), in.length, new CompressorBuffer(seg.asByteBuffer()));

            long[] out = new long[n];
            DocIdCompressor.decompress(new SegmentCompressorBuffer(seg, 0), n, out);

            Assertions.assertArrayEquals(in, out);
        }
    }


}