package nu.marginalia.linkdb;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentDbWriterTest {
    @Test
    public void testCreate() throws IOException {
        Path tempPath = Files.createTempFile("docdb", ".db");
        try {
            var writer = new DocumentDbWriter(tempPath);
            writer.add(new DocdbUrlDetail(
                    1,
                    new EdgeUrl("http", new EdgeDomain("example.com"), null, "/", null),
                    "Test",
                    "This is a test",
                    "en",
                    -4.,
                    "XHTML",
                    5,
                    2020,
                    0xF00BA3,
                    444
            ));
            writer.close();

            var reader = new DocumentDbReader(tempPath);
            var deets = reader.getUrlDetails(new LongArrayList(new long[]{1}));
            System.out.println(deets);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    @Test
    public void testGetUrlDetailsByUrl() throws IOException, SQLException {
        Path tempPath = Files.createTempFile("docdb", ".db");
        try {
            var url1 = new EdgeUrl("http", new EdgeDomain("example.com"), null, "/page1", null);
            var url2 = new EdgeUrl("https", new EdgeDomain("other.org"), null, "/page2", null);

            var writer = new DocumentDbWriter(tempPath);
            writer.add(List.of(
                    new DocdbUrlDetail(1, url1, "First Page", "Description of first page", "en",
                            -4., "XHTML", 5, 2020, 0xF00BA3, 444),
                    new DocdbUrlDetail(2, url2, "Second Page", "Description of second page", "en",
                            -3., "HTML5", 3, 2021, 0xF00BA4, 555)
            ));
            writer.close();

            var reader = new DocumentDbReader(tempPath);

            // Look up both URLs
            var results = reader.getUrlDetailsByUrl(List.of(url1.toString(), url2.toString()));
            assertEquals(2, results.size());

            // Look up a single URL
            results = reader.getUrlDetailsByUrl(List.of(url1.toString()));
            assertEquals(1, results.size());
            assertEquals("First Page", results.getFirst().title());
            assertEquals("Description of first page", results.getFirst().description());

            // Look up a URL that doesn't exist
            results = reader.getUrlDetailsByUrl(List.of("http://nonexistent.com/"));
            assertTrue(results.isEmpty());

            // Empty input
            results = reader.getUrlDetailsByUrl(List.of());
            assertTrue(results.isEmpty());
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }
}
