package nu.marginalia;

import nu.marginalia.ffi.LinuxSystemCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LinuxSystemCallsTest {
    Path testFile;

    @BeforeEach
    public void setUp() throws IOException {
        Assumptions.assumeTrue(LinuxSystemCalls.isAvailable);

        testFile = Files.createTempFile("LinuxSystemCallsTest", ".dat");

        byte[] data = new byte[8192];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        Files.write(testFile, data);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (testFile != null) {
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    public void testFadviseWillneedRange() {
        int fd = LinuxSystemCalls.openBuffered(testFile);
        try (var arena = Arena.ofConfined()) {
            LinuxSystemCalls.fadviseWillneed(fd, 4096, 1024);

            var segment = arena.allocate(1024, 8);
            assertEquals(1024, LinuxSystemCalls.readAt(fd, segment, 4096));
            assertEquals((byte) 4096, segment.get(ValueLayout.JAVA_BYTE, 0));
        }
        finally {
            LinuxSystemCalls.closeFd(fd);
        }
    }
}
