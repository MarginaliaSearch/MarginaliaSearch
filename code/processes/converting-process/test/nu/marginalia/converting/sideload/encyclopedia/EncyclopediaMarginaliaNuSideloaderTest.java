package nu.marginalia.converting.sideload.encyclopedia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class EncyclopediaMarginaliaNuSideloaderTest {
    Path tempFile;

    @BeforeEach
    public void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".dat");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void test() {
        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0x8fa302ffffcffebfL)));
        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0x8fa302ffffcffebfL)));
        System.out.printf("%64s\n", Long.toBinaryString(0xFAAFFFF7F75AA808L));

        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0xa00000L)));
        System.out.printf("%64s\n", Long.toBinaryString(0x20A00000000000L));

        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0x200000L)));
        System.out.printf("%64s\n", Long.toBinaryString(0x200000000004L));

        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0x1000000000000000L)));
        System.out.printf("%64s\n", Long.toBinaryString(0x10L));
    }

}