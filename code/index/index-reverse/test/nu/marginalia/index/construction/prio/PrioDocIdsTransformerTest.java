package nu.marginalia.index.construction.prio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
    public void test() throws IOException {

        // Write 5 longs to the input file as data
        try (var dos = new DataOutputStream(Files.newOutputStream(inputFile))) {
            dos.writeLong(1);
            dos.writeLong(2);
            dos.writeLong(3);
            dos.writeLong(4);
            dos.writeLong(5);
        }

        try (var writeChannel = (FileChannel) Files.newByteChannel(outputFile, StandardOpenOption.WRITE);
             var readChannel = (FileChannel) Files.newByteChannel(inputFile))
        {
            // Transform two segments of the input file and write them to the output file with prefixed sizes
            var transformer = new PrioDocIdsTransformer(writeChannel, readChannel);
            transformer.transform(0, 3);
            transformer.transform(1, 5);
        }

        // Verify the output file
        try (var dis = new DataInputStream(Files.newInputStream(outputFile))) {
            assertEquals(3, dis.readLong());
            assertEquals(1, dis.readLong());
            assertEquals(2, dis.readLong());
            assertEquals(3, dis.readLong());
            assertEquals(2, dis.readLong());
            assertEquals(4, dis.readLong());
            assertEquals(5, dis.readLong());
        }
    }

}