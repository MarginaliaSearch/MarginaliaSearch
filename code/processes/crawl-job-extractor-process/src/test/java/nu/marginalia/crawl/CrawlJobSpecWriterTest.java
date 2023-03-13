package nu.marginalia.crawl;

import nu.marginalia.crawl_plan.CrawlerSpecificationLoader;
import nu.marginalia.crawling.model.CrawlingSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CrawlJobSpecWriterTest {

    Path tempFile;

    @BeforeEach
    public void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), "tmp");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.delete(tempFile);
    }

    @Test
    public void testReadWrite() throws IOException {
        try (CrawlJobSpecWriter writer = new CrawlJobSpecWriter(tempFile)) {
            writer.accept(new CrawlingSpecification("first",1, "test1", List.of("a", "b", "c")));
            writer.accept(new CrawlingSpecification("second",1, "test2", List.of("a", "b", "c", "d")));
            writer.accept(new CrawlingSpecification("third",1, "test3", List.of("a", "b")));
        }

        List<CrawlingSpecification> outputs = new ArrayList<>();
        CrawlerSpecificationLoader.readInputSpec(tempFile, outputs::add);

        assertEquals(outputs.size(), 3);
    }
}
