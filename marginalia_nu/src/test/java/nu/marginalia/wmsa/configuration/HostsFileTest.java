package nu.marginalia.wmsa.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HostsFileTest {
    Path tempFile;

    @BeforeEach
    public void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".tmp");
    }

    @AfterEach
    public void tearDown() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".tmp");
    }

    @Test
    public void testParseSunnyDay() throws IOException {
        Files.writeString(tempFile, """
                # Comment
                edge-index 192.168.0.1
                edge-search 192.168.1.1
                
                auth 127.0.0.55 
                
                
                """);
        var hf = new HostsFile(tempFile);

        Assertions.assertEquals("192.168.0.1", hf.getHost(ServiceDescriptor.EDGE_INDEX));
    }

    @Test
    public void testTooLong() throws IOException {
        Files.writeString(tempFile, """
                edge-index 192.168.0.1 this is where my homie lives
                """);

        assertThrows(IllegalArgumentException.class, () -> new HostsFile(tempFile));
    }

    @Test
    public void testTooShort() throws IOException {
        Files.writeString(tempFile, """
                edge-index 
                """);

        assertThrows(IllegalArgumentException.class, () -> new HostsFile(tempFile));
    }

    @Test
    public void testBadName() throws IOException {
        Files.writeString(tempFile, """
                garum-factory 127.0.0.1
                """);

        new HostsFile(tempFile);
    }
}