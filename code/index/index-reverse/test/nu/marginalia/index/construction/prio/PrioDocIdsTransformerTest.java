package nu.marginalia.index.construction.prio;

import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.sequence.io.BitReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

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

        // Write 5 longs to the input file as data
        try (var dos = new DataOutputStream(Files.newOutputStream(inputFile))) {
            dos.writeLong(UrlIdCodec.encodeId(0, 0));
            dos.writeLong(UrlIdCodec.encodeId(0, 1));
            dos.writeLong(UrlIdCodec.encodeId(1, 0));
            dos.writeLong(UrlIdCodec.encodeId(4, 51) | 0x7000_0000_0000_0000L);
        }

        try (var writeChannel = (FileChannel) Files.newByteChannel(outputFile, StandardOpenOption.WRITE);
             var readChannel = (FileChannel) Files.newByteChannel(inputFile))
        {
            // Transform two segments of the input file and write them to the output file with prefixed sizes
            var transformer = new PrioDocIdsTransformer(writeChannel, readChannel);
            transformer.transform(0, 4);
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

            int diffRank = reader.getGamma() - 1;
            rank += diffRank;
            assertEquals(56, rank);

            domainId = reader.get(31);
            ordinal = reader.get(26);

            assertEquals(4, domainId);
            assertEquals(51, ordinal);
        }
    }

}