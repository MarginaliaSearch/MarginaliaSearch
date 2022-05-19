package nu.marginalia.wmsa.edge.crawler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CrawlJobsSpecificationSetTest {
    @Test
    public void readSet() throws IOException {
        Path tempFile = Files.createTempFile("tmp", "test");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "0\n10\n15");
        var specsSet = new CrawlJobsSpecificationSet(tempFile);
        assertEquals(3, specsSet.size());
        assertEquals(0, specsSet.get(0).pass);
        assertEquals(10, specsSet.get(1).pass);
        assertEquals(15, specsSet.get(2).pass);
    }

    @Test
    public void readSetTrailingJunk() throws IOException {
        Path tempFile = Files.createTempFile("tmp", "test");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "0\n10\n15\n");
        var specsSet = new CrawlJobsSpecificationSet(tempFile);
        assertEquals(3, specsSet.size());
        assertEquals(0, specsSet.get(0).pass);
        assertEquals(10, specsSet.get(1).pass);
        assertEquals(15, specsSet.get(2).pass);
    }

    @Test
    public void readSetEmptyLines() throws IOException {
        Path tempFile = Files.createTempFile("tmp", "test");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "\n0\n10\n\n15\n\n");
        var specsSet = new CrawlJobsSpecificationSet(tempFile);
        assertEquals(3, specsSet.size());
        assertEquals(0, specsSet.get(0).pass);
        assertEquals(10, specsSet.get(1).pass);
        assertEquals(15, specsSet.get(2).pass);
    }

    @Test
    public void readSetEmptyLinesComments() throws IOException {
        Path tempFile = Files.createTempFile("tmp", "test");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "#Hello\n0\n # World\n10\n\n15\n\n");
        var specsSet = new CrawlJobsSpecificationSet(tempFile);
        assertEquals(3, specsSet.size());
        assertEquals(0, specsSet.get(0).pass);
        assertEquals(10, specsSet.get(1).pass);
        assertEquals(15, specsSet.get(2).pass);
    }
}