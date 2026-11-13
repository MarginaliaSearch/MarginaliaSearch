package nu.marginalia.index.reverse.construction;

import nu.marginalia.index.reverse.positions.PositionCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PositionsFileConstructorTest {
    @TempDir
    Path tempDir;

    Path source;
    Path destination;

    @BeforeEach
    public void setUp() {
        source = tempDir.resolve("source");
        destination = tempDir.resolve("destination");
    }

    @Test
    public void testCopyGaps() throws IOException {
        int recordSize = 65535;
        byte[] data = randomBytes(80 * recordSize);
        long[] offsets = writePositions(data, recordSize);

        try (var reader = new PositionsFileReader(source);
             var writer = new PositionsFileConstructor(destination))
        {
            for (int i = 0; i < offsets.length; i += 2) {
                long offset = writer.copyRange(reader,
                        PositionCodec.decodeOffset(offsets[i]), PositionCodec.decodeSize(offsets[i]));
                assertEquals((long) (i / 2) * recordSize, offset);
            }
        }

        byte[] actual = Files.readAllBytes(destination);
        assertEquals(data.length / 2, actual.length);

        for (int i = 0; i < offsets.length; i += 2) {
            int sourceOffset = i * recordSize;
            int destinationOffset = (i / 2) * recordSize;
            assertArrayEquals(
                    Arrays.copyOfRange(data, sourceOffset, sourceOffset + recordSize),
                    Arrays.copyOfRange(actual, destinationOffset, destinationOffset + recordSize));
        }
    }

    @Test
    public void testLargeCopy() throws IOException {
        byte[] data = randomBytes(3 * 1024 * 1024 + 13);
        Files.write(source, data);

        try (var reader = new PositionsFileReader(source);
             var writer = new PositionsFileConstructor(destination))
        {
            assertEquals(0, writer.copyRange(reader, 0, Files.size(source)));
        }

        assertArrayEquals(data, Files.readAllBytes(destination));
    }

    @Test
    public void testClosedReader() throws IOException {
        Files.write(source, new byte[] { 1, 2 });

        try (var reader = new PositionsFileReader(source)) {
            var destination = new ByteArrayOutputStream();
            reader.copyTo(0, 1, destination);
            assertArrayEquals(new byte[] { 1 }, destination.toByteArray());

            reader.close();

            // The second byte is cached, so this must fail without another file read.
            assertThrows(IOException.class, () -> reader.copyTo(1, 1, destination));
        }
    }

    @Test
    public void testClosedWriter() throws IOException {
        byte[] payload = { 1, 2 };

        try (var writer = new PositionsFileConstructor(destination)) {
            writer.add(payload, 0, payload.length);
            writer.close();

            assertArrayEquals(payload, Files.readAllBytes(destination));
            assertThrows(IllegalStateException.class, () -> writer.add(payload, 0, payload.length));
        }
    }

    @Test
    public void testFlushFailure() throws IOException {
        OutputStream output = mock(OutputStream.class);
        IOException failure = new IOException("Failed to flush positions");
        doThrow(failure).when(output).write(any(byte[].class), anyInt(), anyInt());

        try (var files = mockStatic(Files.class)) {
            files.when(() -> Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
                    .thenReturn(output);

            try (var writer = new PositionsFileConstructor(destination)) {
                byte[] payload = { 1, 2 };
                writer.add(payload, 0, payload.length);

                var exception = assertThrows(IOException.class, writer::close);

                assertSame(failure, exception);
                verify(output).close();
                assertThrows(IllegalStateException.class, () -> writer.add(payload, 0, payload.length));
            }
        }
    }

    @Test
    public void testInvalidRanges() throws IOException {
        Files.write(source, new byte[] { 1, 2, 3 });

        try (var reader = new PositionsFileReader(source);
             var writer = new PositionsFileConstructor(destination))
        {
            writer.copyRange(reader, 1, 1);
            assertThrows(IOException.class, () -> writer.copyRange(reader, 0, 1));
            assertThrows(EOFException.class, () -> writer.copyRange(reader, 2, 2));
        }
    }

    private long[] writePositions(byte[] data, int recordSize) throws IOException {
        long[] offsets = new long[data.length / recordSize];
        try (var writer = new PositionsFileConstructor(source)) {
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = writer.add(data, i * recordSize, recordSize);
            }
        }
        return offsets;
    }

    private byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        return data;
    }
}
