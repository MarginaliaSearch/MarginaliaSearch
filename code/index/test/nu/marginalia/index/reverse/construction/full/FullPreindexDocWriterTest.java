package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArrayFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FullPreindexDocWriterTest {
    @TempDir
    Path tempDir;

    @Test
    public void testWriteRaw() throws IOException {
        Path file = tempDir.resolve("docs.dat");
        long[] expected = {
                -9, 90, 91,
                 0, 10, 11,
                 2, 20, 21,
                 4, 40, 41,
                 9, 92, 93
        };

        try (var writer = new FullPreindexDocWriter(file, false)) {
            for (int i = 0; i < expected.length; i += 3) {
                writer.put(expected[i], expected[i + 1], expected[i + 2]);
            }
        }

        verifyPostings(file, false, expected);
    }

    @Test
    public void testWriteCompressed() throws IOException {
        Path file = tempDir.resolve("docs.dat.zst");
        long[] expected = {
                -9, 90, 91,
                 0, 10, 11,
                 2, 20, 21,
                 4, 40, 41,
                 9, 92, 93
        };

        try (var writer = new CompressedFullPreindexDocuments.Writer(file, 2, 1)) {
            for (int i = 0; i < expected.length; i += 3) {
                writer.put(expected[i], expected[i + 1], expected[i + 2]);
            }
        }

        verifyPostings(file, true, expected);
    }

    private void verifyPostings(Path file, boolean compressed, long[] expected) throws IOException {
        try (var result = compressed
                ? CompressedFullPreindexDocuments.open(file)
                : LongArrayFactory.mmapForReadingShared(file))
        {
            assertEquals(expected.length, result.size());
            assertEquals(!compressed, result.hasMemorySegment());

            long[] actual = new long[expected.length];
            result.get(0, actual);
            assertArrayEquals(expected, actual);
        }
    }
}
