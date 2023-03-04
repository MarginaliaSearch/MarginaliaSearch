package nu.marginalia.memex.memex;

import nu.marginalia.util.test.TestUtil;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import nu.marginalia.memex.memex.system.MemexFileWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MemexFileWriterTest {

    Path root;
    MemexFileWriter renderedResources;
    @BeforeEach
    void setUp() throws IOException {
        root = Files.createTempDirectory(getClass().getSimpleName());
        renderedResources = new MemexFileWriter(root);
    }

    @AfterEach
    void tearDown() {
        TestUtil.clearTempDir(root);
    }

    @Test
    void exists() throws IOException {
        assertFalse(renderedResources.exists(new MemexNodeUrl("/test")));
        renderedResources.write(new MemexNodeUrl("/test"), "A line");
        assertTrue(renderedResources.exists(new MemexNodeUrl("/test")));
    }

    @Test
    void write() throws IOException {
        renderedResources.write(new MemexNodeUrl("/test"), "A line");
        assertEquals("A line", Files.readString(root.resolve("test")));
    }
}