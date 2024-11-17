package nu.marginalia.status.db;

import nu.marginalia.status.StatusMetric;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class StatusMetricDbTest {

    @Test
    void saveResult() throws Exception {
        Path tempFile = Files.createTempFile("status-metric-db-test", ".db");
        try {
            StatusMetricDb db = new StatusMetricDb(tempFile.toString());
            assertFalse(db.isOnline("test"));
            db.saveResult(new StatusMetric.MeasurementResult.Success("test", Instant.now(), Duration.ofMillis(50)));
            assertTrue(db.isOnline("test"));
            db.saveResult(new StatusMetric.MeasurementResult.Failure("test", Instant.now()));
            assertFalse(db.isOnline("test"));

            var statistics = db.getStatistics("test");
            System.out.println(statistics);

            assertFalse(statistics.isOnline());
            assertTrue(statistics.numSuccesses() > 0);
            assertTrue(statistics.numFailures() > 0);
            assertEquals(50, statistics.avgRequestTimeMs());
            assertEquals("test", statistics.name());
        }
        finally {
            Files.delete(tempFile);
        }
    }
}