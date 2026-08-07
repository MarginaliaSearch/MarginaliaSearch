package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.ffi.NativeAlgos;
import nu.marginalia.sequence.VarintCodedSequence;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VarintBatchDecodeTest {

    /** The native batch decoder must agree with VarintCodedSequence.values for
     *  sequences spanning all encoded byte widths, including empty sequences */
    @Test
    public void testAgreesWithJavaDecode() {
        assumeTrue(NativeAlgos.isAvailable, "Native library not available");

        Random rng = new Random(1);

        for (int round = 0; round < 500; round++) {
            List<IntList> sequences = new ArrayList<>();

            int n = 1 + rng.nextInt(64);
            for (int i = 0; i < n; i++) {
                sequences.add(randomPositions(rng));
            }

            try (Arena arena = Arena.ofConfined()) {
                long[] addrs = new long[n];
                int[] lens = new int[n];
                int totalBytes = 0;

                List<VarintCodedSequence> encoded = new ArrayList<>(n);
                for (IntList sequence : sequences) {
                    VarintCodedSequence vcs = VarintCodedSequence.generate(sequence);
                    encoded.add(vcs);
                    totalBytes += vcs.bufferSize();
                }

                MemorySegment slab = arena.allocate(Math.max(1, totalBytes));
                int pos = 0;
                for (int i = 0; i < n; i++) {
                    byte[] bytes = new byte[encoded.get(i).bufferSize()];
                    encoded.get(i).buffer().get(bytes);

                    MemorySegment.copy(bytes, 0, slab, ValueLayout.JAVA_BYTE, pos, bytes.length);
                    addrs[i] = slab.address() + pos;
                    lens[i] = bytes.length;
                    pos += bytes.length;
                }

                int[] out = new int[Math.max(1, totalBytes)];
                int[] counts = new int[n];

                long total = NativeAlgos.decodeVarintBatch(addrs, lens, n, out, counts);

                int outIdx = 0;
                for (int i = 0; i < n; i++) {
                    IntList expected = sequences.get(i);
                    assertEquals(expected.size(), counts[i], "round " + round + ", seq " + i);

                    for (int j = 0; j < expected.size(); j++) {
                        assertEquals(expected.getInt(j), out[outIdx + j], "round " + round + ", seq " + i + ", value " + j);
                    }
                    outIdx += counts[i];
                }
                assertEquals(outIdx, (int) total);
            }
        }
    }

    /** Sorted positions whose deltas cover all varint byte widths */
    private IntList randomPositions(Random rng) {
        IntArrayList positions = new IntArrayList();

        int count = rng.nextInt(50);
        int pos = 0;
        for (int i = 0; i < count; i++) {
            int width = rng.nextInt(4);
            int delta = 1 + rng.nextInt(1 << (7 * width + 4));
            pos += delta;
            positions.add(pos);
        }

        return positions;
    }
}
