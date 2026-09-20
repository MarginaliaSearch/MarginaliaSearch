package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FullPreindexDocWriterTest {
    @TempDir
    Path directory;

    @Test
    void testMergePermutations() throws IOException {
        long[] left = { -9, 90, 91, 0, 10, 11, 0, 12, 13, 2, 20, 21, 4, 40, 41, 9, 92, 93 };
        long[] right = { -9, 94, 95, 0, 100, 101, 1, 110, 111, 2, 120, 121, 3, 130, 131, 9, 96, 97 };
        long[] expected = { 0, 10, 11, 1, 110, 111, 2, 20, 21, 3, 130, 131, 4, 40, 41 };
        long[] expectedTails = new long[] { 9, 92, 93, 9, 96, 97 };
        for (boolean leftCompressed : new boolean[] { false, true }) {
            for (boolean rightCompressed : new boolean[] { false, true }) {
                for (boolean outputCompressed : new boolean[] { false, true }) {
                    runTest(left, leftCompressed, right, rightCompressed, expected, expectedTails, outputCompressed);
                }
            }
        }
    }

    public void runTest(long[] left, boolean leftCompressed, long[] right, boolean rightCompressed,
                        long[] expected,
                        long[] expectedTails,
                        boolean outputCompressed)
    throws IOException
    {

        try (var a = input("left", left, leftCompressed);
             var b = input("right", right, rightCompressed)) {

            assertEquals(!leftCompressed, a.hasMemorySegment());
            assertEquals(!rightCompressed, b.hasMemorySegment());

            Path output = directory.resolve("merged");

            try (var writer = new FullPreindexDocWriter(output, outputCompressed)) {
                assertEquals(expected.length, writer.merge(a, b, 3, 15, 3, 15));
                assertEquals(0, writer.merge(a, b, 3, 3, 3, 3));
                assertEquals(3, writer.merge(a, b, 15, 18, 3, 3));
                assertEquals(3, writer.merge(a, b, 3, 3, 15, 18));
            }

            try (var result = FullPreindexDocuments.open(output, outputCompressed).documents) {
                long[] actual = new long[expected.length];

                result.get(0, actual);
                assertArrayEquals(expected, actual);
                assertEquals(expected.length + 6, result.size());

                long[] tails = new long[6];
                result.get(expected.length, tails);
                assertArrayEquals(expectedTails, tails);
            }
        }

    }

    private LongArray input(String name, long[] values, boolean compressed) throws IOException {
        Path file = directory.resolve(name);
        try (var source = LongArrayFactory.wrap(MemorySegment.ofArray(values))) {
            if (compressed) {
                // Small blocks make merges cross decoding boundaries.
                try (var writer = new CompressedFullPreindexDocuments.Writer(file, 2, 1)) {
                    writer.put(source, 0, source.size());
                }
            }
            else {
                try (var writer = new FullPreindexDocWriter(file, false)) {
                    writer.put(source, 0, source.size());
                }
            }
        }
        return FullPreindexDocuments.open(file, compressed).documents;
    }
}
