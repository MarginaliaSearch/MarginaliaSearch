package nu.marginalia.crawl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
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

            assertEquals(allFields, db.getSummary("all.marginalia.nu").orElseThrow());
            assertEquals(minFields, db.getSummary("min.marginalia.nu").orElseThrow());

            var updatedAllFields = new DomainStateDb.SummaryRecord(
                    "all.marginalia.nu",
                    Instant.now(),
                    "BAD",
                    null,
                    null
            );

            db.save(updatedAllFields);
            assertEquals(updatedAllFields, db.getSummary("all.marginalia.nu").orElseThrow());
        }
    }

    @Test
    public void testMetadata() throws SQLException {
        try (var db = new DomainStateDb(tempFile)) {
            var original = new DomainStateDb.CrawlMeta("example.com", Instant.ofEpochMilli(12345), Duration.ofMillis(30), Duration.ofMillis(300), 1, 2, 3);
            db.save(original);

            var maybeMeta = db.getMeta("example.com");
            assertTrue(maybeMeta.isPresent());
            assertEquals(original, maybeMeta.get());
        }
    }

    @Test
    public void testGetLastFullCrawlTimes() throws SQLException {
        try (var db = new DomainStateDb(tempFile)) {
            db.save(new DomainStateDb.CrawlMeta("a.example.com", Instant.ofEpochMilli(1000), Duration.ZERO, Duration.ZERO, 0, 0, 0));
            db.save(new DomainStateDb.CrawlMeta("b.example.com", Instant.ofEpochMilli(2000), Duration.ZERO, Duration.ZERO, 0, 0, 0));

            var times = db.getLastFullCrawlTimes();

            assertEquals(2, times.size());
            assertEquals(1000L, times.get("a.example.com"));
            assertEquals(2000L, times.get("b.example.com"));
            assertNull(times.get("never-crawled.example.com"));
        }
    }

    @Test
    public void testGetLastFullCrawlTimesNoConnection() throws SQLException {
        try (var db = new DomainStateDb((Path) null)) {
            assertTrue(db.getLastFullCrawlTimes().isEmpty());
        }
    }

    @Test
    public void testDeleteDomain() throws SQLException {
        try (var db = new DomainStateDb(tempFile)) {
            db.save(new DomainStateDb.CrawlMeta("gone.example.com", Instant.ofEpochMilli(1000), Duration.ZERO, Duration.ZERO, 0, 0, 0));
            db.save(DomainStateDb.SummaryRecord.forSuccess("gone.example.com"));
            db.saveIcon("gone.example.com", new DomainStateDb.FaviconRecord("text/plain", "x".getBytes()));

            db.save(new DomainStateDb.CrawlMeta("stays.example.com", Instant.ofEpochMilli(2000), Duration.ZERO, Duration.ZERO, 0, 0, 0));

            db.deleteDomain("gone.example.com");

            assertTrue(db.getMeta("gone.example.com").isEmpty());
            assertTrue(db.getSummary("gone.example.com").isEmpty());
            assertTrue(db.getIcon("gone.example.com").isEmpty());

            // an unrelated domain is left untouched
            assertTrue(db.getMeta("stays.example.com").isPresent());
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