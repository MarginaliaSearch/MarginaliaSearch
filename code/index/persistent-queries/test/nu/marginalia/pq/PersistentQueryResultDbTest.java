package nu.marginalia.pq;

import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;


class PersistentQueryResultDbTest {
    @TempDir
    public Path tempDir;

    @Test
    public void test() throws IOException, SQLException, URISyntaxException {
        try (var db = new PersistentQueryResultDb(Files.createTempFile(tempDir, "test", ".db"))) {

            Assertions.assertTrue(db.addResult(new EdgeUrl("https://www.marginalia.nu/"), "testTitle", Instant.now()));
            Assertions.assertFalse(db.addResult(new EdgeUrl("https://www.marginalia.nu/"), "testTitle", Instant.now()));

            var results = db.getResultsSince(Instant.MIN, Integer.MIN_VALUE, 5);

            System.out.println(results);

            Assertions.assertEquals(1, results.size());

        }
    }

    @Test
    public void testAddSummary() throws Exception {
        try (var db = new PersistentQueryResultDb(Files.createTempFile(tempDir, "test", ".db"))) {
            db.addSummary(Instant.now(), 4);
        }
    }

}