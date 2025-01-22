package nu.marginalia.crawl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DomainStateDbTest {

    Path tempFile;
    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".db");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testSummaryRecord() throws SQLException {
        try (var db = new DomainStateDb(tempFile)) {
            var allFields = new DomainStateDb.SummaryRecord(
                    "all.marginalia.nu",
                    Instant.now(),
                    "OK",
                    "Bad address",
                    "https://www.marginalia.nu/atom.xml"
                    );

            var minFields = new DomainStateDb.SummaryRecord(
                    "min.marginalia.nu",
                    Instant.now(),
                    "OK",
                    null,
                    null
            );

            db.save(allFields);
            db.save(minFields);

            assertEquals(allFields, db.get("all.marginalia.nu").orElseThrow());
            assertEquals(minFields, db.get("min.marginalia.nu").orElseThrow());

            var updatedAllFields = new DomainStateDb.SummaryRecord(
                    "all.marginalia.nu",
                    Instant.now(),
                    "BAD",
                    null,
                    null
            );

            db.save(updatedAllFields);
            assertEquals(updatedAllFields, db.get("all.marginalia.nu").orElseThrow());
        }
    }

    @Test
    public void testFavicon() throws SQLException {
        try (var db = new DomainStateDb(tempFile)) {
            db.saveIcon("www.marginalia.nu", new DomainStateDb.FaviconRecord("text/plain", "hello world".getBytes()));

            var maybeData = db.getIcon("www.marginalia.nu");
            assertTrue(maybeData.isPresent());
            var actualData = maybeData.get();

            assertEquals("text/plain", actualData.contentType());
            assertArrayEquals("hello world".getBytes(), actualData.imageData());

            maybeData = db.getIcon("foobar");
            assertTrue(maybeData.isEmpty());
        }
    }

}