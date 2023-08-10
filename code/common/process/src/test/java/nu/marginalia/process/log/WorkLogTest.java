package nu.marginalia.process.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkLogTest {

    private Path logFile;
    @BeforeEach
    public void setUp() throws IOException {
        logFile = Files.createTempFile("worklog", ".log");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(logFile);
    }


    @Test
    public void testLog() throws IOException {
        var log = new WorkLog(logFile);
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
        WorkLog log = new WorkLog(logFile);
        log.setJobToFinished("A", "a.txt",1);
        log.setJobToFinished("B", "b.txt",2);
        log.setJobToFinished("C", "c.txt",3);
        log.close();
        log = new WorkLog(logFile);
        log.setJobToFinished("E", "e.txt",4);
        assertTrue(log.isJobFinished("A"));
        assertTrue(log.isJobFinished("B"));
        assertTrue(log.isJobFinished("C"));
        assertTrue(log.isJobFinished("E"));
        log.close();

        Files.readAllLines(logFile).forEach(System.out::println);
    }

    @Test
    public void test() {
        try (var workLog = new WorkLog(logFile)) {
            workLog.setJobToFinished("test", "loc1", 4);
            workLog.setJobToFinished("test2", "loc2", 5);
            workLog.setJobToFinished("test3", "loc3", 1);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        try (var workLog = new WorkLog(logFile)) {
            workLog.setJobToFinished("test4", "loc4", 0);

            assertTrue(workLog.isJobFinished("test"));
            assertTrue(workLog.isJobFinished("test2"));
            assertTrue(workLog.isJobFinished("test3"));
            assertTrue(workLog.isJobFinished("test4"));
            assertFalse(workLog.isJobFinished("test5"));
        }
        catch (Exception e) {
            e.printStackTrace();
            fail();
        }


        Map<String, WorkLogEntry> entriesById = new HashMap<>();
        WorkLog.iterable(logFile).forEach(e -> entriesById.put(e.id(), e));

        assertEquals(4, entriesById.size());

        assertEquals("loc1", entriesById.get("test").path());
        assertEquals("loc2", entriesById.get("test2").path());
        assertEquals("loc3", entriesById.get("test3").path());
        assertEquals("loc4", entriesById.get("test4").path());

    }
}