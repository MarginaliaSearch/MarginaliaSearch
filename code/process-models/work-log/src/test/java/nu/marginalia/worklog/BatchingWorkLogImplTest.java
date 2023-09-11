package nu.marginalia.worklog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BatchingWorkLogImplTest {
    Path fileName;

    @BeforeEach
    public void setUp() throws IOException {
        fileName = Files.createTempFile(getClass().getSimpleName(), ".test");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(fileName);
    }

    @Test
    public void testResumeOnEmptyFile() throws IOException {
        Files.delete(fileName);

        try (var wl = new BatchingWorkLogImpl(fileName)) {
            wl.logItem("1");
            wl.logItem("2");
            wl.logItem("3");
            wl.logFinishedBatch();
            wl.logItem("4");
            wl.logItem("5");
            wl.logFinishedBatch();
            wl.logItem("6");
        }

        try (var wl = new BatchingWorkLogImpl(fileName)) {
            assertTrue(wl.isItemCommitted("1"));
            assertTrue(wl.isItemCommitted("2"));
            assertTrue(wl.isItemCommitted("3"));
            assertTrue(wl.isItemCommitted("4"));
            assertTrue(wl.isItemCommitted("5"));
            assertFalse(wl.isItemCommitted("6"));
            wl.logItem("7");
            wl.logFinishedBatch();
        }
        try (var wl = new BatchingWorkLogImpl(fileName)) {
            assertTrue(wl.isItemCommitted("1"));
            assertTrue(wl.isItemCommitted("2"));
            assertTrue(wl.isItemCommitted("3"));
            assertTrue(wl.isItemCommitted("4"));
            assertTrue(wl.isItemCommitted("5"));
            assertFalse(wl.isItemCommitted("6"));
            assertTrue(wl.isItemCommitted("7"));
        }

        Files.readAllLines(fileName).forEach(System.out::println);
    }
}