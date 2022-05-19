package nu.marginalia.wmsa.edge.crawling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkLogTest {
    Path outFile;
    @BeforeEach
    public void setUp() throws IOException {
        outFile = Files.createTempFile(getClass().getSimpleName(), ".log");
    }
    @AfterEach
    public void tearDown() throws IOException {
        Files.delete(outFile);
    }

    @Test
    public void testLog() throws IOException {
        var log = new WorkLog(outFile);
        log.setJobToFinished("A", "a.txt",1);
        log.setJobToFinished("B", "b.txt",2);
        log.setJobToFinished("C", "c.txt",3);
        assertTrue(log.isJobFinished("A"));
        assertTrue(log.isJobFinished("B"));
        assertTrue(log.isJobFinished("C"));
        assertFalse(log.isJobFinished("E"));
    }

    @Test
    public void testLogResume() throws Exception {
        WorkLog log = new WorkLog(outFile);
        log.setJobToFinished("A", "a.txt",1);
        log.setJobToFinished("B", "b.txt",2);
        log.setJobToFinished("C", "c.txt",3);
        log.close();
        log = new WorkLog(outFile);
        log.setJobToFinished("E", "e.txt",4);
        assertTrue(log.isJobFinished("A"));
        assertTrue(log.isJobFinished("B"));
        assertTrue(log.isJobFinished("C"));
        assertTrue(log.isJobFinished("E"));
        log.close();

        Files.readAllLines(outFile).forEach(System.out::println);
    }

}
