package nu.marginalia.rwf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RandomFileAssemblerTest {

    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("RandomFileAssemblerTest");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.delete(tempDir);
    }

    @Test
    public void testConsistentByteOrder() throws IOException {
        // A potential foot-gun in mixing MemorySegment and ByteBuffer-based I/O is that
        // bytebuffer defaults to Java's big endian order, and memory segment does not.

        // This test verifies that the encoded data is in fact identical between methods.
        try (RandomFileAssembler assembler1 = RandomFileAssembler.ofTempFiles(tempDir, 1);
            RandomFileAssembler assembler2 = RandomFileAssembler.ofMmap(tempDir, 1);
            RandomFileAssembler assembler3 = RandomFileAssembler.ofInMemoryAsssembly(1)) {

            assembler1.put(0, 0x123456789abcdef0L);
            assembler2.put(0, 0x123456789abcdef0L);
            assembler3.put(0, 0x123456789abcdef0L);

            assembler1.write(tempDir.resolve("file1"));
            assembler2.write(tempDir.resolve("file2"));
            assembler3.write(tempDir.resolve("file3"));

            // Some of these methods may "overshoot" the size a bit, only compare the range we are
            // interested in...

            System.out.println(Files.size(tempDir.resolve("file1")));
            System.out.println(Files.size(tempDir.resolve("file2")));
            System.out.println(Files.size(tempDir.resolve("file3")));

            byte[] bytes1 = Files.readAllBytes(tempDir.resolve("file1"));
            byte[] bytes2 = Files.readAllBytes(tempDir.resolve("file2"));
            byte[] bytes3 = Files.readAllBytes(tempDir.resolve("file3"));

            assertArrayEquals(bytes2, bytes3);
            assertArrayEquals(bytes1, bytes2);
        } finally {
            Files.deleteIfExists(tempDir.resolve("file1"));
            Files.deleteIfExists(tempDir.resolve("file2"));
            Files.deleteIfExists(tempDir.resolve("file3"));
        }
    }
}