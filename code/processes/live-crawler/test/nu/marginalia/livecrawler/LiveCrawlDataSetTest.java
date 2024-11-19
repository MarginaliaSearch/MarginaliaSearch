package nu.marginalia.livecrawler;

import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class LiveCrawlDataSetTest {

    @Test
    public void testGetDataSet() throws Exception {
        Path tempFile = Files.createTempFile("test", ".db");
        try {
            LiveCrawlDataSet dataSet = new LiveCrawlDataSet(tempFile.toString());

            Assertions.assertFalse(dataSet.hasUrl("https://www.example.com/"));
            dataSet.saveDocument(
                    1,
                    new EdgeUrl("https://www.example.com/"),
                    "test",
                    "test",
                    "test"
            );
            Assertions.assertTrue(dataSet.hasUrl("https://www.example.com/"));
        }
        finally {
            Files.delete(tempFile);
        }
    }

}