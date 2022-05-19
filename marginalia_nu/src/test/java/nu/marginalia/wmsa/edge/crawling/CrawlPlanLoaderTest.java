package nu.marginalia.wmsa.edge.crawling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CrawlPlanLoaderTest {

    Path tempFile;

    @BeforeEach
    public void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".yaml");
    }
    @AfterEach
    public void tearDown() throws IOException {
        Files.delete(tempFile);
    }

    @Test
    void load() throws IOException {
        Files.writeString(tempFile, """
                jobSpec: "job.spec"
                crawl:
                    dir: "/foo"
                    logName: "foo.log"
                process:
                    dir: "/bar"
                    logName: "bar.log"
                """);
        var loader = new CrawlPlanLoader();
        var ret = loader.load(tempFile);

        assertEquals(Path.of("job.spec"), ret.getJobSpec());

        assertEquals(Path.of("/foo"), ret.crawl.getDir());
        assertEquals(Path.of("/foo/foo.log"), ret.crawl.getLogFile());

        assertEquals(Path.of("/bar"), ret.process.getDir());
        assertEquals(Path.of("/bar/bar.log"), ret.process.getLogFile());

        System.out.println(ret);
    }
}