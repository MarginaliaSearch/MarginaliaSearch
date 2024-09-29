package nu.marginalia.index.construction.prio;

import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.sequence.io.BitReader;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PrioDocIdsTransformerTest {

    Path inputFile = null;
    Path outputFile = null;

    @BeforeEach
    public void setUp() throws IOException {
        inputFile = Files.createTempFile("input", ".dat");
        outputFile = Files.createTempFile("output", ".dat");
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (inputFile != null) {
            Files.deleteIfExists(inputFile);
        }
        if (outputFile != null) {
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    public void testDomainIdDocOrd() throws IOException {


        try (var writeChannel = (FileChannel) Files.newByteChannel(inputFile, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);

            buffer.putLong(UrlIdCodec.encodeId(0, 0));
            buffer.putLong(UrlIdCodec.encodeId(0, 1));
            buffer.putLong(UrlIdCodec.encodeId(1, 0));
            buffer.putLong(UrlIdCodec.encodeId(4, 51) | 0x7000_0000_0000_0000L);

            writeChannel.write(buffer.flip());

            buffer.clear();

            buffer.putLong(UrlIdCodec.encodeId(0, 0));
            buffer.putLong(UrlIdCodec.encodeId(0, 1));
            buffer.putLong(UrlIdCodec.encodeId(1, 0));
            buffer.putLong(UrlIdCodec.encodeId(4, 51) | 0x7000_0000_0000_0000L);

            writeChannel.write(buffer.flip());
        }

        try (var writeChannel = (FileChannel) Files.newByteChannel(outputFile, StandardOpenOption.WRITE);
             var readChannel = (FileChannel) Files.newByteChannel(inputFile);
             var transformer = new PrioDocIdsTransformer(writeChannel, readChannel))
        {
            // Transform two segments of the input file and write them to the output file with prefixed sizes

            long pos1 = transformer.transform(0, 4);
            long pos2 = transformer.transform(1, 8);

            // The functions return the positions in the output file, which should be non-zero for all but the first segment
            assertEquals(0, pos1);
            assertNotEquals(0, pos2);
        }

        byte[] bytes = Files.readAllBytes(outputFile);
        var buffer = ByteBuffer.wrap(bytes);


        BitReader reader = new BitReader(buffer);

        // read the header
        {
            int code = reader.get(2);
            int size = reader.get(30);
            assertEquals(3, code);
            assertEquals(4, size);
        }

        // read first doc id in parts
        int rank = reader.get(7);
        int domainId = reader.get(31);
        int ordinal = reader.get(26);

        assertEquals(0, rank);
        assertEquals(0, domainId);
        assertEquals(0, ordinal);

        {
            int code = reader.get(2);
            assertEquals(0, code); // increment doc ordinal

            int dord = reader.getGamma();
            ordinal += dord;

            assertEquals(1, ordinal);
        }

        {
            int code = reader.get(2);
            assertEquals(1, code); // increment doc ordinal

            int diffDomainId = reader.getDelta();
            domainId += diffDomainId;
            assertEquals(1, domainId);

            int abs_ord = reader.getDelta();
            ordinal = abs_ord - 1;
            assertEquals(0, ordinal);
        }

        {
            int code = reader.get(2);
            assertEquals(2, code); // increment doc ordinal

            int diffRank = reader.getGamma();
            rank += diffRank;
            assertEquals(56, rank);

            domainId = reader.get(31);
            ordinal = reader.get(26);

            assertEquals(4, domainId);
            assertEquals(51, ordinal);
        }
    }

}