package nu.marginalia.index.reverse.construction.prio;

import nu.marginalia.index.reverse.PrioIndexVByteEntrySource;
import nu.marginalia.model.id.UrlIdCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

class PrioDocIdsVByteTransformerTest {

    Path inputFile;
    Path outputFile;

    @BeforeEach
    public void setUp() throws IOException {
        inputFile = Files.createTempFile("input", ".dat");
        outputFile = Files.createTempFile("output", ".dat");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(inputFile);
        Files.deleteIfExists(outputFile);
    }

    @Test
    public void testRoundTrip() throws IOException {
        long[] docIds = {
                UrlIdCodec.encodeId(0, 0),
                UrlIdCodec.encodeId(0, 1),
                UrlIdCodec.encodeId(1, 0),
                UrlIdCodec.encodeId(4, 51) | 0x7000_0000_0000_0000L
        };

        // Write input data
        try (FileChannel writeChannel = (FileChannel) Files.newByteChannel(inputFile, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate(docIds.length * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (long id : docIds) {
                buffer.putLong(id);
            }
            writeChannel.write(buffer.flip());
        }

        // Transform
        try (FileChannel writeChannel = (FileChannel) Files.newByteChannel(outputFile, StandardOpenOption.WRITE);
             FileChannel readChannel = (FileChannel) Files.newByteChannel(inputFile);
             PrioDocIdsVByteTransformer transformer = new PrioDocIdsVByteTransformer(writeChannel, readChannel))
        {
            long pos = transformer.transform(0, docIds.length);
            assertEquals(0, pos);
        }

        // Read back using PrioIndexVByteEntrySource
        try (FileChannel readChannel = (FileChannel) Files.newByteChannel(outputFile)) {
            var lqb = new nu.marginalia.array.page.LongQueryBuffer(32);
            PrioIndexVByteEntrySource source = new PrioIndexVByteEntrySource("test", "term", readChannel, 0);

            source.read(lqb);

            assertEquals(docIds.length, lqb.size());
            long[] result = lqb.copyData();
            for (int i = 0; i < docIds.length; i++) {
                assertEquals(docIds[i], result[i], "Mismatch at index " + i);
            }
            assertFalse(source.hasMore());
        }
    }

    @Test
    public void testRoundTripWithDuplicates() throws IOException {
        long[] docIds = {
                UrlIdCodec.encodeId(0, 0),
                UrlIdCodec.encodeId(0, 0),  // duplicate
                UrlIdCodec.encodeId(0, 1),
                UrlIdCodec.encodeId(1, 0),
        };

        try (FileChannel writeChannel = (FileChannel) Files.newByteChannel(inputFile, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate(docIds.length * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (long id : docIds) {
                buffer.putLong(id);
            }
            writeChannel.write(buffer.flip());
        }

        try (FileChannel writeChannel = (FileChannel) Files.newByteChannel(outputFile, StandardOpenOption.WRITE);
             FileChannel readChannel = (FileChannel) Files.newByteChannel(inputFile);
             PrioDocIdsVByteTransformer transformer = new PrioDocIdsVByteTransformer(writeChannel, readChannel))
        {
            transformer.transform(0, docIds.length);
        }

        try (FileChannel readChannel = (FileChannel) Files.newByteChannel(outputFile)) {
            var lqb = new nu.marginalia.array.page.LongQueryBuffer(32);
            PrioIndexVByteEntrySource source = new PrioIndexVByteEntrySource("test", "term", readChannel, 0);

            source.read(lqb);

            // Should have 3 distinct entries after deduplication
            assertEquals(3, lqb.size());
        }
    }

    @Test
    public void testLargeSegment() throws IOException {
        // Enough entries with large deltas to exceed the 64KB write buffer,
        // exercising the multi-batch flush path
        int count = 10000;
        long[] docIds = new long[count];
        for (int i = 0; i < count; i++) {
            docIds[i] = UrlIdCodec.encodeId(i, i * 7);
        }

        try (FileChannel writeChannel = (FileChannel) Files.newByteChannel(inputFile, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate(count * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (long id : docIds) {
                buffer.putLong(id);
            }
            writeChannel.write(buffer.flip());
        }

        try (FileChannel writeChannel = (FileChannel) Files.newByteChannel(outputFile, StandardOpenOption.WRITE);
             FileChannel readChannel = (FileChannel) Files.newByteChannel(inputFile);
             PrioDocIdsVByteTransformer transformer = new PrioDocIdsVByteTransformer(writeChannel, readChannel))
        {
            long pos = transformer.transform(0, count);
            assertEquals(0, pos);
        }

        // Read back and verify all entries
        try (FileChannel readChannel = (FileChannel) Files.newByteChannel(outputFile)) {
            var lqb = new nu.marginalia.array.page.LongQueryBuffer(count + 16);
            PrioIndexVByteEntrySource source = new PrioIndexVByteEntrySource("test", "term", readChannel, 0);

            source.read(lqb);

            assertEquals(count, lqb.size());
            long[] result = lqb.copyData();
            for (int i = 0; i < count; i++) {
                assertEquals(docIds[i], result[i], "Mismatch at index " + i);
            }
            assertFalse(source.hasMore());
        }
    }

    @Test
    public void testMultipleSegments() throws IOException {
        long[] segment1 = {
                UrlIdCodec.encodeId(0, 0),
                UrlIdCodec.encodeId(0, 1),
        };
        long[] segment2 = {
                UrlIdCodec.encodeId(1, 0),
                UrlIdCodec.encodeId(4, 51),
        };

        try (FileChannel writeChannel = (FileChannel) Files.newByteChannel(inputFile, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate((segment1.length + segment2.length) * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (long id : segment1) buffer.putLong(id);
            for (long id : segment2) buffer.putLong(id);
            writeChannel.write(buffer.flip());
        }

        long pos1;
        long pos2;
        try (FileChannel writeChannel = (FileChannel) Files.newByteChannel(outputFile, StandardOpenOption.WRITE);
             FileChannel readChannel = (FileChannel) Files.newByteChannel(inputFile);
             PrioDocIdsVByteTransformer transformer = new PrioDocIdsVByteTransformer(writeChannel, readChannel))
        {
            pos1 = transformer.transform(0, segment1.length);
            pos2 = transformer.transform(0, segment1.length + segment2.length);
        }

        assertEquals(0, pos1);
        assertTrue(pos2 > 0, "Second segment should start at a non-zero offset");

        // Verify both segments are readable
        try (FileChannel readChannel = (FileChannel) Files.newByteChannel(outputFile)) {
            var lqb = new nu.marginalia.array.page.LongQueryBuffer(32);

            PrioIndexVByteEntrySource source1 = new PrioIndexVByteEntrySource("test", "t1", readChannel, pos1);
            source1.read(lqb);
            assertEquals(2, lqb.size());
            assertEquals(segment1[0], lqb.copyData()[0]);
            assertEquals(segment1[1], lqb.copyData()[1]);

            PrioIndexVByteEntrySource source2 = new PrioIndexVByteEntrySource("test", "t2", readChannel, pos2);
            source2.read(lqb);
            assertEquals(2, lqb.size());
            assertEquals(segment2[0], lqb.copyData()[0]);
            assertEquals(segment2[1], lqb.copyData()[1]);
        }
    }
}
